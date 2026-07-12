use std::{f64::consts::PI, net::SocketAddr};

use axum::{
    Json, Router,
    body::Body,
    extract::{Path, State},
    http::{HeaderValue, StatusCode, header},
    response::{IntoResponse, Response as HttpResponse},
    routing::{get, post},
};
use geojson::{GeoJson, Geometry, Value};
use serde::{Deserialize, Serialize};
use tonic::{Request, Response, Status, transport::Server};
use tower_http::{cors::CorsLayer, trace::TraceLayer};
use tracing::info;
use uuid::Uuid;

pub mod proto {
    tonic::include_proto!("orbitamarket.geo.v1");
}
use proto::{
    AnalyzeRequest as GrpcRequest, AnalyzeResponse as GrpcResponse,
    geo_pricing_server::{GeoPricing, GeoPricingServer},
};

const EARTH_RADIUS_M: f64 = 6_371_008.8;

#[derive(Clone, Default)]
struct GeoEngine;

#[derive(Deserialize)]
struct AnalyzeRequest {
    geojson: serde_json::Value,
    product_type: String,
    resolution_m: Option<f64>,
}

#[derive(Deserialize)]
struct SnapshotRequest {
    bbox: [f64; 4],
    width: Option<u32>,
    height: Option<u32>,
}

#[derive(Clone, Serialize)]
struct AnalyzeResponse {
    valid: bool,
    error: Option<String>,
    area_sq_km: f64,
    resolution_m: f64,
    price_geocredits: i64,
    bbox: Vec<f64>,
    normalized_geojson: serde_json::Value,
    quote_id: String,
}

impl GeoEngine {
    fn analyze(
        &self,
        raw: &str,
        product_type: &str,
        requested_resolution_m: f64,
    ) -> Result<AnalyzeResponse, String> {
        let parsed: GeoJson = raw
            .parse()
            .map_err(|error| format!("Invalid GeoJSON: {error}"))?;
        let geometry = match parsed {
            GeoJson::Geometry(value) => value,
            GeoJson::Feature(feature) => feature.geometry.ok_or("Feature has no geometry")?,
            GeoJson::FeatureCollection(_) => {
                return Err("FeatureCollection is not supported".into());
            }
        };
        let ring = match &geometry.value {
            Value::Polygon(rings) if !rings.is_empty() => &rings[0],
            _ => return Err("AOI must be a GeoJSON Polygon".into()),
        };
        validate_ring(ring)?;
        let area_sq_km = spherical_area(ring) / 1_000_000.0;
        if !(0.000_1..=5_000_000.0).contains(&area_sq_km) {
            return Err("AOI area is outside allowed limits".into());
        }
        let bbox = bounding_box(ring);
        if !(0.25..=10.0).contains(&requested_resolution_m) {
            return Err("Resolution must be between 0.25 and 10 metres".into());
        }
        let (base_price, area_tariff) = match product_type.to_ascii_uppercase().as_str() {
            "ARCHIVE" => (20.0, 0.8),
            "TASKING" => (90.0, 2.5),
            "MONITORING" => (180.0, 4.0),
            _ => return Err("Unknown product type".into()),
        };
        // Finer ground sampling distance requires more expensive acquisition and processing.
        // Map zoom is deliberately excluded: it only changes presentation, not the requested AOI.
        let resolution_factor = (1.0 / requested_resolution_m).clamp(0.35, 4.0);
        let price = ((base_price + area_sq_km * area_tariff) * resolution_factor).ceil() as i64;
        let normalized = serde_json::to_value(Geometry::new(Value::Polygon(vec![ring.clone()])))
            .map_err(|error| error.to_string())?;
        Ok(AnalyzeResponse {
            valid: true,
            error: None,
            area_sq_km: (area_sq_km * 100.0).round() / 100.0,
            resolution_m: requested_resolution_m,
            price_geocredits: price,
            bbox,
            normalized_geojson: normalized,
            quote_id: Uuid::new_v4().to_string(),
        })
    }
}

fn validate_ring(ring: &[Vec<f64>]) -> Result<(), String> {
    if ring.len() < 4 {
        return Err("Polygon needs at least three vertices".into());
    }
    if ring.first() != ring.last() {
        return Err("Polygon ring must be closed".into());
    }
    for point in ring {
        if point.len() < 2
            || !(-180.0..=180.0).contains(&point[0])
            || !(-90.0..=90.0).contains(&point[1])
        {
            return Err("Coordinates are outside WGS84 bounds".into());
        }
    }
    for first in 0..ring.len() - 1 {
        for second in first + 2..ring.len() - 1 {
            if first == 0 && second == ring.len() - 2 {
                continue;
            }
            if segments_intersect(
                &ring[first],
                &ring[first + 1],
                &ring[second],
                &ring[second + 1],
            ) {
                return Err("Polygon has self-intersections".into());
            }
        }
    }
    Ok(())
}

fn segments_intersect(a: &[f64], b: &[f64], c: &[f64], d: &[f64]) -> bool {
    fn cross(a: &[f64], b: &[f64], c: &[f64]) -> f64 {
        (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    }
    let (c1, c2, c3, c4) = (
        cross(a, b, c),
        cross(a, b, d),
        cross(c, d, a),
        cross(c, d, b),
    );
    c1 * c2 < 0.0 && c3 * c4 < 0.0
}

fn spherical_area(ring: &[Vec<f64>]) -> f64 {
    let mut sum = 0.0;
    for pair in ring.windows(2) {
        let lon1 = pair[0][0] * PI / 180.0;
        let lon2 = pair[1][0] * PI / 180.0;
        let lat1 = pair[0][1] * PI / 180.0;
        let lat2 = pair[1][1] * PI / 180.0;
        let mut delta = lon2 - lon1;
        if delta > PI {
            delta -= 2.0 * PI;
        } else if delta < -PI {
            delta += 2.0 * PI;
        }
        sum += delta * (2.0 + lat1.sin() + lat2.sin());
    }
    (sum * EARTH_RADIUS_M * EARTH_RADIUS_M / 2.0).abs()
}

fn bounding_box(ring: &[Vec<f64>]) -> Vec<f64> {
    let (mut west, mut south, mut east, mut north) = (180.0_f64, 90.0_f64, -180.0_f64, -90.0_f64);
    for point in ring {
        west = west.min(point[0]);
        east = east.max(point[0]);
        south = south.min(point[1]);
        north = north.max(point[1]);
    }
    vec![west, south, east, north]
}

async fn analyze(
    State(engine): State<GeoEngine>,
    Json(request): Json<AnalyzeRequest>,
) -> impl IntoResponse {
    match engine.analyze(
        &request.geojson.to_string(),
        &request.product_type,
        request.resolution_m.unwrap_or(0.8),
    ) {
        Ok(response) => (StatusCode::OK, Json(response)).into_response(),
        Err(error) => (
            StatusCode::BAD_REQUEST,
            Json(serde_json::json!({"error_code":"INVALID_AOI","message":error})),
        )
            .into_response(),
    }
}

async fn health() -> &'static str {
    "ok"
}

async fn snapshot(
    Json(request): Json<SnapshotRequest>,
) -> Result<HttpResponse, (StatusCode, String)> {
    let [west, south, east, north] = request.bbox;
    if west >= east
        || south >= north
        || !(-180.0..=180.0).contains(&west)
        || !(-180.0..=180.0).contains(&east)
        || !(-90.0..=90.0).contains(&south)
        || !(-90.0..=90.0).contains(&north)
    {
        return Err((StatusCode::BAD_REQUEST, "Invalid bbox".into()));
    }
    let width = request.width.unwrap_or(1200).clamp(320, 1600);
    let height = request.height.unwrap_or(800).clamp(240, 1200);
    let bbox = format!("{west},{south},{east},{north}");
    let url = format!(
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/export?bbox={}&bboxSR=4326&imageSR=4326&size={},{}&format=jpg&f=image",
        urlencoding::encode(&bbox),
        width,
        height
    );
    let upstream = reqwest::get(url)
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error.to_string()))?;
    if !upstream.status().is_success() {
        return Err((
            StatusCode::BAD_GATEWAY,
            "Imagery provider rejected request".into(),
        ));
    }
    let bytes = upstream
        .bytes()
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error.to_string()))?;
    let mut response = HttpResponse::new(Body::from(bytes));
    response
        .headers_mut()
        .insert(header::CONTENT_TYPE, HeaderValue::from_static("image/jpeg"));
    response.headers_mut().insert(
        header::CACHE_CONTROL,
        HeaderValue::from_static("private, max-age=300"),
    );
    response.headers_mut().insert(
        "x-imagery-source",
        HeaderValue::from_static("Esri World Imagery"),
    );
    Ok(response)
}

async fn tile(
    Path((provider, z, y, x)): Path<(String, u8, u32, u32)>,
) -> Result<HttpResponse, (StatusCode, String)> {
    if z > 19 {
        return Err((StatusCode::BAD_REQUEST, "Unsupported zoom".into()));
    }
    let url = match provider.as_str() {
        "satellite" => format!(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        ),
        "street" => format!("https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        _ => return Err((StatusCode::NOT_FOUND, "Unknown tile provider".into())),
    };
    let client = reqwest::Client::builder()
        .user_agent("OrbitaMarket-Industrial-Lab/1.0")
        .build()
        .map_err(|error| (StatusCode::INTERNAL_SERVER_ERROR, error.to_string()))?;
    let upstream = client
        .get(url)
        .send()
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error.to_string()))?;
    if !upstream.status().is_success() {
        return Err((
            StatusCode::BAD_GATEWAY,
            "Tile provider rejected request".into(),
        ));
    }
    let content_type = upstream
        .headers()
        .get(header::CONTENT_TYPE)
        .cloned()
        .unwrap_or_else(|| HeaderValue::from_static("image/jpeg"));
    let bytes = upstream
        .bytes()
        .await
        .map_err(|error| (StatusCode::BAD_GATEWAY, error.to_string()))?;
    let mut response = HttpResponse::new(Body::from(bytes));
    response
        .headers_mut()
        .insert(header::CONTENT_TYPE, content_type);
    response.headers_mut().insert(
        header::CACHE_CONTROL,
        HeaderValue::from_static("public, max-age=300"),
    );
    Ok(response)
}

#[tonic::async_trait]
impl GeoPricing for GeoEngine {
    async fn analyze(
        &self,
        request: Request<GrpcRequest>,
    ) -> Result<Response<GrpcResponse>, Status> {
        let value = request.into_inner();
        let resolution_m = if value.resolution_m > 0.0 {
            value.resolution_m
        } else {
            0.8
        };
        let result = GeoEngine::analyze(self, &value.geojson, &value.product_type, resolution_m)
            .map_err(Status::invalid_argument)?;
        Ok(Response::new(GrpcResponse {
            valid: result.valid,
            error: String::new(),
            area_sq_km: result.area_sq_km,
            resolution_m: result.resolution_m,
            price_geocredits: result.price_geocredits,
            bbox: result.bbox,
            normalized_geojson: result.normalized_geojson.to_string(),
            quote_id: result.quote_id,
        }))
    }
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::from_default_env()
                .add_directive("geo_pricing_service=info".parse()?),
        )
        .init();
    let engine = GeoEngine;
    let http = Router::new()
        .route("/health", get(health))
        .route("/api/v1/geo/analyze", post(analyze))
        .route("/api/v1/geo/snapshot", post(snapshot))
        .route("/api/v1/geo/tiles/{provider}/{z}/{y}/{x}", get(tile))
        .with_state(engine.clone())
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http());
    let http_address: SocketAddr = "0.0.0.0:8090".parse()?;
    let grpc_address: SocketAddr = "0.0.0.0:50051".parse()?;
    info!(%http_address, %grpc_address, "geo-pricing service started");
    tokio::try_join!(
        async {
            axum::serve(tokio::net::TcpListener::bind(http_address).await?, http)
                .await
                .map_err(Box::<dyn std::error::Error>::from)
        },
        async {
            Server::builder()
                .add_service(GeoPricingServer::new(engine))
                .serve(grpc_address)
                .await
                .map_err(Box::<dyn std::error::Error>::from)
        }
    )?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn calculates_moscow_aoi_and_server_price() {
        let geojson = r#"{"type":"Polygon","coordinates":[[[37.45,55.65],[37.82,55.65],[37.82,55.87],[37.45,55.65]]]}"#;
        let result = GeoEngine.analyze(geojson, "ARCHIVE", 0.8).unwrap();
        assert!(result.area_sq_km > 100.0);
        assert!(result.price_geocredits > 20);
        assert_eq!(result.bbox, vec![37.45, 55.65, 37.82, 55.87]);
    }
    #[test]
    fn rejects_self_intersection() {
        let geojson = r#"{"type":"Polygon","coordinates":[[[0,0],[1,1],[0,1],[1,0],[0,0]]]}"#;
        assert!(GeoEngine.analyze(geojson, "TASKING", 0.8).is_err());
    }
}

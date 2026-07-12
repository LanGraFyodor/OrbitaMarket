import { useEffect, useRef, useState } from "react";
import L, {
  type CircleMarker,
  type Map as LeafletMap,
  type Polygon,
  type Polyline,
} from "leaflet";
import "leaflet/dist/leaflet.css";
import { Check, Crosshair, Layers3, RotateCcw } from "lucide-react";

type Coordinate = [number, number];
const streetTiles = "/geo/api/v1/geo/tiles/street/{z}/{y}/{x}";
const streetAttribution = "© OpenStreetMap contributors";

const polygonData = (coordinates: Coordinate[]) => ({
  type: "Feature" as const,
  properties: {},
  geometry: { type: "Polygon" as const, coordinates: [coordinates] },
});

const toWkt = (coordinates: Coordinate[]) =>
  `POLYGON((${coordinates.map(([lng, lat]) => `${lng.toFixed(6)} ${lat.toFixed(6)}`).join(",")}))`;

export interface CapturedFrame {
  image: string;
  captured_at: string;
  center: [number, number];
  zoom: number;
  source: string;
}

export function SatelliteMissionMap({
  onAoiChange,
  onValidityChange,
}: {
  onAoiChange: (wkt: string, geojson: ReturnType<typeof polygonData>) => void;
  onValidityChange: (valid: boolean) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const map = useRef<LeafletMap | null>(null);
  const tileLayer = useRef<L.TileLayer | null>(null);
  const coordinates = useRef<Coordinate[]>([]);
  const markers = useRef<CircleMarker[]>([]);
  const line = useRef<Polyline | null>(null);
  const polygon = useRef<Polygon | null>(null);
  const drawingRef = useRef(false);
  const [drawing, setDrawing] = useState(false);
  const [vertexCount, setVertexCount] = useState(0);
  const [mapError, setMapError] = useState(false);

  const clearLayers = () => {
    const instance = map.current;
    if (!instance) return;
    markers.current.forEach((marker) => marker.removeFrom(instance));
    markers.current = [];
    line.current?.removeFrom(instance);
    polygon.current?.removeFrom(instance);
    line.current = null;
    polygon.current = null;
  };

  const drawOpenAoi = () => {
    const instance = map.current;
    if (!instance) return;
    line.current?.removeFrom(instance);
    if (coordinates.current.length >= 2) {
      line.current = L.polyline(
        coordinates.current.map(([lng, lat]) => [lat, lng] as [number, number]),
        { color: "#d8ff3e", weight: 3, dashArray: "8 7" },
      ).addTo(instance);
    }
  };

  useEffect(() => {
    if (!container.current) return;
    const instance = L.map(container.current, {
      center: [55.75, 37.62],
      zoom: 10,
      minZoom: 3,
      maxZoom: 19,
      zoomControl: false,
      attributionControl: true,
      preferCanvas: false,
    });
    instance.attributionControl.setPrefix(false);
    const base = L.tileLayer(streetTiles, {
      minZoom: 3,
      maxZoom: 19,
      attribution: streetAttribution,
      crossOrigin: true,
    });
    base.on("tileload", () => setMapError(false));
    base.on("tileerror", () => setMapError(true));
    base.addTo(instance);
    L.control.zoom({ position: "bottomright" }).addTo(instance);
    tileLayer.current = base;
    map.current = instance;

    instance.on("click", (event) => {
      if (!drawingRef.current) return;
      const coordinate: Coordinate = [event.latlng.lng, event.latlng.lat];
      coordinates.current = [...coordinates.current, coordinate];
      const marker = L.circleMarker(event.latlng, {
        radius: 7,
        color: "#07100e",
        weight: 3,
        fillColor: "#d8ff3e",
        fillOpacity: 1,
      }).addTo(instance);
      markers.current.push(marker);
      setVertexCount(coordinates.current.length);
      drawOpenAoi();
    });

    requestAnimationFrame(() => instance.invalidateSize());
    return () => {
      instance.remove();
      map.current = null;
      tileLayer.current = null;
    };
  }, []);

  const reset = () => {
    clearLayers();
    coordinates.current = [];
    drawingRef.current = true;
    setVertexCount(0);
    setDrawing(true);
    onValidityChange(false);
  };

  const stopDrawing = () => {
    drawingRef.current = false;
    setDrawing(false);
  };

  const finishDrawing = () => {
    const instance = map.current;
    if (!instance || coordinates.current.length < 3) return;
    line.current?.removeFrom(instance);
    line.current = null;
    const closed = [...coordinates.current, coordinates.current[0]];
    coordinates.current = closed;
    polygon.current = L.polygon(
      closed.map(([lng, lat]) => [lat, lng] as [number, number]),
      {
        color: "#d8ff3e",
        weight: 3,
        fillColor: "#d8ff3e",
        fillOpacity: 0.22,
      },
    ).addTo(instance);
    onAoiChange(toWkt(closed), polygonData(closed));
    onValidityChange(true);
    stopDrawing();
    setVertexCount(closed.length - 1);
  };

  return (
    <div className="real-map-shell leaflet-mission-map">
      <div ref={container} className="real-map" />
      {mapError && (
        <div className="map-error">
          Подложка временно недоступна. Переключите слой или повторите загрузку.
        </div>
      )}
      <div className="map-mode">
        <span className="map-layer-label">
          <Layers3 /> Обычная карта
        </span>
        <button
          className={drawing ? "active" : ""}
          onClick={() => (drawing ? stopDrawing() : reset())}
        >
          <Crosshair /> {drawing ? "Рисование AOI" : "Выбрать AOI"}
        </button>
        {drawing && (
          <button
            className="finish-aoi"
            disabled={vertexCount < 3}
            onClick={finishDrawing}
          >
            <Check /> Готово · {vertexCount}
          </button>
        )}
        <button title="Очистить AOI" onClick={reset}>
          <RotateCcw />
        </button>
      </div>
      <div className="map-help">
        {drawing
          ? vertexCount < 3
            ? `Поставьте ещё ${3 - vertexCount} точ.`
            : "Нажмите «Готово», чтобы замкнуть территорию"
          : vertexCount
            ? "AOI подтверждён · цена рассчитана Rust"
            : "Нажмите «Выбрать AOI» и отметьте территорию"}
      </div>
      <div className="capture-panel">
        <span>
          <i /> СПУТНИКОВЫЙ ПРОДУКТ ФОРМИРУЕТСЯ ПОСЛЕ ОПЛАТЫ
        </span>
      </div>
    </div>
  );
}

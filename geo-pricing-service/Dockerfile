FROM rust:1.93-bookworm AS build
WORKDIR /app
COPY geo-pricing-service/Cargo.toml geo-pricing-service/build.rs ./
COPY geo-pricing-service/proto ./proto
COPY geo-pricing-service/src ./src
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/release/geo-pricing-service /usr/local/bin/geo-pricing-service
EXPOSE 8090 50051
ENTRYPOINT ["geo-pricing-service"]

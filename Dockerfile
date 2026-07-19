# Backend: the http4s/tapir routing server, packaged on the host by `sbt stage`.
# Graph, scores, and area config are mounted at runtime (compose volumes), not baked in.
FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/universal/stage/ /app/

ENV SCENIC_AREA=areas/berlin.toml \
    SCENIC_HOST=0.0.0.0 \
    SCENIC_PORT=8080 \
    JAVA_OPTS=-Xmx2g

EXPOSE 8080
ENTRYPOINT ["/app/bin/scenic-route"]

# syntax=docker/dockerfile:1

# --- Build the distribution --------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY lecturo-relay/ lecturo-relay/
RUN chmod +x gradlew \
    && ./gradlew -p lecturo-relay --no-daemon installDist

# --- Runtime -----------------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 lecturo
WORKDIR /app
COPY --from=build /src/lecturo-relay/build/install/lecturo-relay/ ./
# Gradle copies cached dependency jars as 0600; the runtime user must read them.
RUN chmod -R a+rX /app
ENV RELAY_PORT=8787 \
    QUOTA_FILE=/data/relay-quota.json
EXPOSE 8787
USER lecturo
ENTRYPOINT ["/app/bin/lecturo-relay"]

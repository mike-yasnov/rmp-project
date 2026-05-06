FROM gradle:8.13-jdk17 AS build

WORKDIR /src
COPY . .
RUN gradle --no-daemon buildFatJar

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG OTEL_JAVA_AGENT_VERSION=2.14.0
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN curl -fsSL \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    -o /app/opentelemetry-javaagent.jar
RUN useradd --system --user-group app
USER app
COPY --from=build /src/build/libs/api-gateway-all.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]

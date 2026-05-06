FROM gradle:8.13-jdk17 AS build

WORKDIR /src
COPY . .
RUN gradle --no-daemon buildFatJar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --user-group app
USER app
COPY --from=build /src/build/libs/core-banking-all.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

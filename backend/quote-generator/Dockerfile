FROM gradle:8.13-jdk17 AS build

WORKDIR /src
COPY . .
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --user-group app
USER app
COPY --from=build /src/build/libs/quote-generator-all.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

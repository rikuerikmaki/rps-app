FROM gradle:8.8-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/backend ./
ENV PORT=8080
EXPOSE 8080
CMD ["./bin/backend"]


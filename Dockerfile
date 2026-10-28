FROM eclipse-temurin:21.0.9_10-jdk-alpine AS build
WORKDIR /build
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src
RUN ./gradlew --no-daemon bootJar -x test
FROM eclipse-temurin:21.0.9_10-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build --chown=app:app /build/build/libs/app.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=12 CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java","-jar","app.jar"]

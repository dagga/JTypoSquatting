# Backend images, built from the project root:
#   docker build -f backend/Dockerfile --target api    -t jtyposquatting-api .
#   docker build -f backend/Dockerfile --target worker -t jtyposquatting-worker .

FROM docker.io/library/eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle gradle
COPY shared shared
COPY backend backend
# Declared in settings.gradle: Gradle needs the directory to configure the build
COPY frontend frontend
# gradle.properties may point to a developer JDK: use the one of the image
RUN ./gradlew :backend:bootJar --no-daemon -x test -Dorg.gradle.java.home="$JAVA_HOME"

# API instances: REST API and SSE streams, no browser
FROM docker.io/library/eclipse-temurin:21-jre AS api
RUN useradd --system --uid 10001 jtypo
COPY --from=build /src/backend/build/libs/jtyposquatting-backend.jar /app/app.jar
USER 10001
ENV SPRING_PROFILES_ACTIVE=api \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    STORAGE_TYPE=s3 \
    FORWARD_HEADERS_STRATEGY=framework \
    SESSION_COOKIE_SECURE=true
# 8081: API, 8082: monitoring (health, Prometheus), never routed by the reverse proxy
EXPOSE 8081 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

# Workers: process scans and open hostile pages with Chromium. The Playwright image provides
# Java 21 and the browser matching the playwright-java version (1.63).
FROM mcr.microsoft.com/playwright/java:v1.63.0-noble AS worker
COPY --from=build /src/backend/build/libs/jtyposquatting-backend.jar /app/app.jar
# Unprivileged user of the image
USER pwuser
ENV SPRING_PROFILES_ACTIVE=worker \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs \
    STORAGE_TYPE=s3 \
    PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
# Monitoring only (health, Prometheus)
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

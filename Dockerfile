# syntax=docker/dockerfile:1
# Runtime-only image: the jar is built beforehand (./gradlew bootJar), not inside this
# Dockerfile - see scripts/build-image.sh for local dev and ci.yml's "Build jar" step for
# CI. This keeps the GitHub Packages credentials one-piece-exception needs (see
# build.gradle.kts) confined to wherever Gradle actually runs, with no BuildKit secret
# plumbing needed here - `docker build` itself never resolves dependencies.
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY build/libs/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

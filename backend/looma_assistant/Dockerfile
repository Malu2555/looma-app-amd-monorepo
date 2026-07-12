# ============================================================================
#  Multi-stage Dockerfile — Looma Assistant Backend
#
#  Stage 1 : Build  — compile with Maven (dependency caching via BuildKit)
#  Stage 2 : Run    — minimal JRE image with non-root user
#
#  NOTE: Pin base images by digest for reproducible builds, e.g.
#    FROM maven:3.9-eclipse-temurin-21@sha256:<digest>
#    FROM eclipse-temurin:21-jre@sha256:<digest>
#  Run `docker buildx imagetools inspect <image:tag>` to get the current digest.
# ============================================================================

# ----------------------------------------------------------------------------
#  Stage 1 — Build
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

# Tiered compilation speeds up the build; no need to relocate the Maven home
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"

WORKDIR /workspace

# 1. Copy only POM first — this layer is cached unless dependencies change
COPY pom.xml .

# 2. Copy the rest of the source
COPY src ./src

# 3. Build the application. The BuildKit cache mount persists ~/.m2 across
#    builds so dependencies are not re-downloaded on every source change.
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B --no-transfer-progress

# ----------------------------------------------------------------------------
#  Stage 2 — Run
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# Security: run as a dedicated non-root system user
RUN groupadd --system --gid 5001 looma \
    && useradd --system --gid looma --uid 5001 --shell /usr/sbin/nologin looma

WORKDIR /app

# Copy the built JAR from the builder stage (explicit name avoids surprises)
COPY --from=build --chown=looma:looma /workspace/target/looma_assistant-0.0.1-SNAPSHOT.jar app.jar

# Use the prod profile so the container reads application-prod.yml with real API keys
ENV SPRING_PROFILES_ACTIVE=prod
# Faster JVM startup in containerized environments
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# NOTE: This is a headless CLI job (prod profile, WebApplicationType.NONE) — it
# reads /input/tasks.json and writes /output/results.json, then exits. There is
# no web server, so EXPOSE and a curl-based HEALTHCHECK are intentionally omitted
# (a healthcheck against :8080 would always fail and flag the container unhealthy).

# Forward SIGTERM to the JVM so Spring Boot shuts down gracefully
STOPSIGNAL SIGTERM

# Drop privileges
USER looma

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
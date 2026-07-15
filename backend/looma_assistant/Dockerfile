# ============================================================================
#  Multi-stage Dockerfile — Looma Assistant Backend (+ synchronous Python runner)
#
#  Stage 1 : Build  — compile the Java backend with Maven (dependency caching)
#  Stage 2 : Run    — JRE + Python3 + bash; runs the synchronous runner layer
#
#  NOTE: Pin base images by digest for reproducible builds, e.g.
#    FROM maven:3.9-eclipse-temurin-21@sha256:<digest>
#    FROM eclipse-temurin:21-jre@sha256:<digest>
#
#  Build context is the `backend/` directory (set in docker-compose.yml) so the
#  image can contain BOTH the Java backend (looma_assistant/) and the
#  synchronous Python runner (runner/, requirements.text).
# ============================================================================

# ----------------------------------------------------------------------------
#  Stage 1 — Build
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

# Tiered compilation speeds up the build; no need to relocate the Maven home
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"

WORKDIR /workspace

# 1. Copy only POM first — this layer is cached unless dependencies change
COPY looma_assistant/pom.xml .

# 2. Copy the rest of the source
COPY looma_assistant/src ./src

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

# Install Python 3, pip, and bash for the synchronous runner layer.
# python3-venv is included so the runner can isolate its deps if desired.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        python3-venv \
        bash \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR from the builder stage (explicit name avoids surprises)
COPY --from=build --chown=looma:looma /workspace/target/looma_assistant-0.0.1-SNAPSHOT.jar app.jar

# Copy the synchronous runner layer (Python orchestrator + deps manifest)
COPY --chown=looma:looma runner/main.py /app/runner/main.py
COPY --chown=looma:looma runner.bash /app/runner.bash
COPY --chown=looma:looma requirements.text /app/requirements.text

# Install the Python dependencies for the synchronous layer
RUN pip3 install --no-cache-dir -r /app/requirements.text

# Use the prod profile so the container reads application-prod.yml with real API keys
ENV SPRING_PROFILES_ACTIVE=prod
# Faster JVM startup in containerized environments
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# NOTE: This is a headless CLI job driven by the synchronous runner layer.
# The runner reads /input/tasks.json and writes /output/results.json, then
# exits. There is no web server, so EXPOSE and a curl-based HEALTHCHECK are
# intentionally omitted (a healthcheck against :8080 would always fail).

# Forward SIGTERM to the runner so the synchronous process shuts down gracefully
STOPSIGNAL SIGTERM

# Drop privileges
USER looma

# The bash entrypoint is the single synchronous, signal-forwarding process the
# autograder launches. It installs nothing at runtime (deps are baked in) and
# exec's the Python orchestrator, which blocks until results.json is written.
ENTRYPOINT ["bash", "/app/runner.bash"]
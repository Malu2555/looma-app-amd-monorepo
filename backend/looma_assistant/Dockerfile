# ============================================================================
#  Multi-stage Dockerfile — Looma Assistant Backend
#
#  Stage 1 : Build  — compile with Maven (dependency caching)
#  Stage 2 : Run    — minimal JRE image with non-root user
# ============================================================================

# ----------------------------------------------------------------------------
#  Stage 1 — Build
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

# Prevent Maven from writing to home dir in CI
ENV MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Duser.home=/tmp/maven-home"

WORKDIR /workspace

# 1. Copy only POM first — this layer is cached unless dependencies change
COPY pom.xml .

# 2. Download all dependencies offline (cached layer)
RUN mvn dependency:go-offline -B --no-transfer-progress

# 3. Copy the rest of the source
COPY src ./src

# 4. Build the application (skip tests for speed; run tests separately in CI)
RUN mvn clean package -DskipTests -B --no-transfer-progress

# 5. Extract the JAR version-agnostically for the next stage
RUN cp target/*.jar /workspace/app.jar

# ----------------------------------------------------------------------------
#  Stage 2 — Run
# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

# Security: run as non-root user
RUN groupadd --system --gid 1000 looma \
    && useradd --system --gid looma --uid 1000 --create-home --shell /sbin/nologin looma

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=build --chown=looma:looma /workspace/app.jar app.jar

# Faster Tomcat startup in containerized environments
ENV JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose the default Spring Boot port
EXPOSE 8080

# Health check — Spring Boot actuator or a simple TCP check
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD java -jar app.jar --health || curl -f http://localhost:8080/api/agent/health || exit 1

# Drop privileges
USER looma

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
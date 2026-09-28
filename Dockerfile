# syntax=docker/dockerfile:1
#
# Builds one runnable Spring Boot module into a deployable image. The target
# module and its server port are chosen with build args, so the same Dockerfile
# serves every application (dcc-web on 8080, dcc-mcp on 8081, ...).
# Build context is the repository root; .dockerignore prunes the context.
#
# Note: the build uses the maven image's own `mvn` rather than ./mvnw, because
# .mvn/wrapper/maven-wrapper.jar is git-ignored and absent in a fresh checkout.

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE=dcc-web
WORKDIR /workspace

# Resolve dependencies from the POMs first, so source edits reuse this layer.
COPY pom.xml ./
COPY dcc-core/pom.xml dcc-core/pom.xml
COPY dcc-application/pom.xml dcc-application/pom.xml
COPY dcc-bootstrap/pom.xml dcc-bootstrap/pom.xml
COPY dcc-security/pom.xml dcc-security/pom.xml
COPY dcc-web/pom.xml dcc-web/pom.xml
COPY dcc-mcp/pom.xml dcc-mcp/pom.xml
RUN mvn -B -q dependency:go-offline

# Copy the rest of the context (respects .dockerignore) and build the selected
# runnable module (and the modules it depends on) only - the image ships just
# that module's executable jar.
COPY . .
RUN mvn -B -DskipTests -pl ${MODULE} -am package

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
ARG MODULE=dcc-web
ARG SERVER_PORT=8080
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/${MODULE}/target/${MODULE}-*.jar app.jar
USER app
# SERVER_PORT also drives Spring Boot's server.port (relaxed binding), so the
# exposed/health-checked port stays in sync with the application.
ENV SERVER_PORT=${SERVER_PORT}
EXPOSE ${SERVER_PORT}
# Scale the JVM heap with the container memory limit (see APP_MEMORY_LIMIT).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:${SERVER_PORT}/actuator/health || exit 1
# JAVA_OPTS lets the deployment tune the JVM (e.g. -Xmx512m).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

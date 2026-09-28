# syntax=docker/dockerfile:1
#
# Builds the runnable service (dcc-starter) into a deployable image.
# Build context is the repository root; .dockerignore prunes the context.
#
# Note: the build uses the maven image's own `mvn` rather than ./mvnw, because
# .mvn/wrapper/maven-wrapper.jar is git-ignored and absent in a fresh checkout.

# ---------------------------------------------------------------------------
# Build stage
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Resolve dependencies from the POMs first, so source edits reuse this layer.
COPY pom.xml ./
COPY dcc-core/pom.xml dcc-core/pom.xml
COPY dcc-api/pom.xml dcc-api/pom.xml
COPY dcc-starter/pom.xml dcc-starter/pom.xml
RUN mvn -B -q dependency:go-offline

# Copy the rest of the context (respects .dockerignore) and build the runnable
# module (and any module dependencies it grows) only - the image ships just
# dcc-starter's executable jar.
COPY . .
RUN mvn -B -DskipTests -pl dcc-starter -am package

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl \
 && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/dcc-starter/target/dcc-starter-*.jar app.jar
USER app
EXPOSE 8080
# Scale the JVM heap with the container memory limit (see APP_MEMORY_LIMIT).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
# JAVA_OPTS lets the deployment tune the JVM (e.g. -Xmx512m).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

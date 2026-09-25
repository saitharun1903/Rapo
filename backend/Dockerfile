# syntax=docker/dockerfile:1

# ---- Build: compile and package with the Maven wrapper, then split the jar into Spring Boot's layers ----------
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes. The cache mount keeps ~/.m2 across builds.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (backend-ci), not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests -Djacoco.skip=true package \
    && mkdir target/app \
    && java -Djarmode=tools -jar "$(ls target/rideflow-backend-*.jar | grep -v plain)" \
        extract --layers --launcher --destination target/app

# ---- Runtime: JRE only, non-root, one layer per rate of change ------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S rideflow && adduser -S -G rideflow -H rideflow
WORKDIR /app

# Least to most frequently changed, so a code change rebuilds only the last, small layer.
COPY --from=build --chown=rideflow:rideflow /workspace/target/app/dependencies/ ./
COPY --from=build --chown=rideflow:rideflow /workspace/target/app/spring-boot-loader/ ./
COPY --from=build --chown=rideflow:rideflow /workspace/target/app/snapshot-dependencies/ ./
COPY --from=build --chown=rideflow:rideflow /workspace/target/app/application/ ./

USER rideflow
# The container's memory limit, not the host's, sizes the heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# 8080: API and WebSocket. 8081: actuator (health, Prometheus); keep it off the public internet.
EXPOSE 8080 8081

# Readiness, not liveness: the container counts as healthy once it can take traffic.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-8081}/actuator/health/readiness" || exit 1

# Exec form: the JVM is PID 1 and receives SIGTERM directly, so Spring shuts down gracefully.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

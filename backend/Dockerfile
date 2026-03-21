# ── Build stage ───────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies separately so this layer is cached
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S medoq && adduser -S medoq -G medoq

COPY --from=build /app/target/*.jar app.jar
RUN chown medoq:medoq app.jar
USER medoq

EXPOSE 8080

# Health check — Spring Boot Actuator /health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/v1/actuator/health | grep -q '"status":"UP"' || exit 1

# JVM flags:
#  -XX:+UseContainerSupport          → respect cgroup memory limits
#  -XX:MaxRAMPercentage=75.0         → use 75% of container memory as heap
#  -XX:+ExitOnOutOfMemoryError       → crash fast instead of degraded state
#  -Djava.security.egd=file:/dev/urandom → faster startup on Linux
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.egd=file:/dev/urandom", \
  "-jar", "app.jar"]

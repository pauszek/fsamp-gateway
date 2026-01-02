# =============================================================================
# FSAMP Gateway - Multi-stage Dockerfile
# =============================================================================
# Build: docker build -t fsamp-gateway .
# Run:   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=local fsamp-gateway
# =============================================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for better caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (cached if pom.xml unchanged)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Update Alpine packages to fix CVEs + install curl for health checks and discovery
RUN apk upgrade --no-cache && \
    apk add --no-cache curl

# Security: run as non-root user
RUN addgroup -g 1001 -S fsamp && \
    adduser -u 1001 -S fsamp -G fsamp

WORKDIR /app

# Copy entrypoint script
COPY scripts/docker-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Create config directory for shared volumes
RUN mkdir -p /config && chown fsamp:fsamp /config

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Switch to non-root user
USER fsamp

# JVM options for containers + FIPS mode
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dorg.bouncycastle.fips.approved_only=true"

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]

# =============================================================================
# FSAMP Gateway - Multi-stage Dockerfile
# =============================================================================
# Build: docker build -t fsamp-gateway .
# Run:   docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=local fsamp-gateway
# =============================================================================

# Stage 1: Build
# Using Amazon Corretto 21 for FIPS 140-3 validated cryptography (ACCP)
FROM amazoncorretto:21-al2023 AS builder

WORKDIR /app

# Install Maven wrapper dependencies
RUN dnf install -y findutils tar gzip && dnf clean all

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
# Amazon Corretto 21 includes ACCP (Amazon Corretto Crypto Provider)
# ACCP is FIPS 140-3 Level 1 validated — provides FIPS-compliant TLS and crypto
FROM amazoncorretto:21-al2023-headless

# Install curl for health checks and shadow-utils for useradd
RUN dnf install -y --allowerasing curl shadow-utils && dnf clean all

# Security: run as non-root user
RUN groupadd -g 1001 fsamp && \
    useradd -u 1001 -g fsamp -s /sbin/nologin fsamp

WORKDIR /app

# Copy entrypoint script
COPY scripts/docker-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Copy built JAR (SBOM included in JAR at META-INF/sbom/application.cdx.json)
COPY --from=builder /app/target/*.jar app.jar

# Create config directory for shared volumes
RUN mkdir -p /config && chown fsamp:fsamp /config

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Switch to non-root user
USER fsamp

# JVM options for containers + FIPS mode
# - ACCP with FIPS mode provides FIPS 140-3 Level 1 validated TLS and crypto
# - BouncyCastle FIPS approved_only restricts to NIST-validated algorithms
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dorg.bouncycastle.fips.approved_only=true \
    -Dcom.amazon.corretto.crypto.provider.extclasses=FIPS"

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]

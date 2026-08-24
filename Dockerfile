FROM amazoncorretto:26-al2023 AS builder

WORKDIR /app

# Builder packages intentionally follow the AL2023 repositories from the pinned
# base image so rebuilds receive current security fixes.
# hadolint ignore=DL3041
RUN dnf install -y findutils tar gzip && dnf clean all

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src src
COPY schema schema
RUN ./mvnw package -DskipTests -B

FROM amazoncorretto:26-al2023-headless

# AL2023 pins repositories to the base image's release snapshot. Upgrade the
# complete runtime against the latest AL2023 release so newly disclosed fixes
# are not limited to a manually maintained package list.
# Runtime packages intentionally follow the current AL2023 security channel.
# hadolint ignore=DL3041
RUN dnf --refresh --releasever=latest upgrade -y && \
    dnf install -y --allowerasing curl shadow-utils && \
    dnf clean all && \
    rpm -e --nodeps python3-pip-wheel

RUN groupadd -g 1001 fsamp && \
    useradd -u 1001 -g fsamp -s /sbin/nologin fsamp

WORKDIR /app

COPY scripts/docker-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

COPY --from=builder /app/target/*.jar app.jar

RUN mkdir -p /config && chown fsamp:fsamp /config

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

USER fsamp

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Dorg.bouncycastle.fips.approved_only=true"
# ACCP installation is performed programmatically by FipsCryptoConfig
# at application startup (assertHealthy + Security.insertProviderAt). The
# previous extclasses property is not part of the ACCP API and was removed
# to avoid misleading auditors looking for the FIPS posture in flags.

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]

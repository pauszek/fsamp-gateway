# FSAMP Gateway

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![FIPS 140-3](https://img.shields.io/badge/FIPS-140--3-blue)](https://csrc.nist.gov/publications/detail/fips/140/3/final)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Enterprise-grade, FIPS 140-3-oriented file upload microservice for the FSAMP platform.

**FSAMP Gateway** is the secure ingress point for the FedRAMP Moderate-aligned Secure AWS Microservices Platform. It handles file uploads with AWS KMS-backed encryption, content validation, and event-driven architecture for downstream processing.

**[Full Architecture Documentation](docs/ARCHITECTURE.md)**

## Key Features

| Feature | Description |
|---------|-------------|
| **FIPS 140-3-oriented security** | ACCP and BC-FIPS providers with approved algorithm policy |
| **KMS Encryption** | Server-side AES-256-GCM encryption via AWS KMS |
| **Content Validation** | Apache Tika-based MIME detection (anti-spoofing) |
| **Resilience Patterns** | Circuit breaker, retry, rate limiting (Resilience4j) |
| **Idempotency** | DynamoDB-backed duplicate prevention |
| **OAuth2 Security** | AWS Cognito JWT validation |
| **Observability** | Metrics, structured logging, distributed tracing |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for LocalStack)
- [fsamp-infra](https://github.com/pauszek/fsamp-infra) (LocalStack setup)

### Run Locally

```bash
cd ../fsamp-infra && make up && make apply-local
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
open http://localhost:8080/swagger-ui.html
```

### Build & Test

```bash
# Run tests
./mvnw test

# Build JAR
./mvnw package -DskipTests

# Build Docker image
docker build -t fsamp-gateway .
```

## API Reference

### Upload File

```bash
curl -X POST http://localhost:8080/api/v1/files/upload \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "X-Idempotency-Key: $(uuidgen)" \
  -F "file=@document.pdf" \
  -F "correlationId=my-trace-123"
```

**Response** (`201 Created`):
```json
{
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "correlationId": "my-trace-123",
  "filename": "document.pdf",
  "sizeBytes": 102400,
  "mimeType": "application/pdf",
  "status": "UPLOADED",
  "uploadedAt": "2026-01-01T12:00:00.000Z",
  "message": "File uploaded successfully and queued for processing"
}
```

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/files/upload` | Upload a file |
| `GET` | `/api/v1/files/{id}` | Get file metadata |
| `DELETE` | `/api/v1/files/{id}` | Delete file (admin only) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/swagger-ui.html` | API documentation |

## Architecture

The gateway follows **Hexagonal Architecture** (Ports & Adapters):

```
┌─────────────────────────────────────────────────────────────────┐
│                        File Upload Flow                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Client ──▶ REST API ──▶ Domain Service ──┬──▶ S3 (KMS)         │
│              │                            │                      │
│              ▼                            ├──▶ SNS (events)      │
│        Validation (Tika)                  │                      │
│        SHA-256 Checksum                   └──▶ DynamoDB          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Project Structure

```
src/main/java/io/github/pauszek/fsampgateway/
├── adapter/           # Infrastructure adapters (REST, S3, SNS, DynamoDB)
│   ├── in/web/        # REST controllers
│   └── out/           # Storage, messaging, crypto adapters
├── application/       # DTOs, mappers, use case orchestration
├── domain/            # Core business logic (framework-agnostic)
│   ├── model/         # Entities & Value Objects
│   ├── port/          # Interfaces (in/out)
│   └── service/       # Domain services
└── infrastructure/    # Spring configs, security, observability
```

**[Detailed Architecture Docs](docs/ARCHITECTURE.md)**

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Profile (`local`, `dev`, `staging`, `prod`) | `local` |
| `AWS_REGION` | AWS region | `us-west-2` |
| `S3_BUCKET_NAME` | S3 bucket for files | `fsamp-files-local` |
| `KMS_KEY_ID` | KMS key ID/alias | `alias/fsamp-files-key` |
| `SNS_TOPIC_ARN` | SNS topic for events | - |
| `COGNITO_USER_POOL_ID` | Cognito User Pool ID | - |

### Profiles

| Profile | Description |
|---------|-------------|
| `local` | LocalStack, FIPS disabled |
| `dev` | AWS dev environment |
| `staging` | AWS staging environment (FIPS enabled) |
| `prod` | FIPS endpoints and fail-closed crypto posture |

---

## Testing

```bash
# Unit tests
./mvnw test

# Integration tests (Testcontainers)
./mvnw verify -P integration-tests

# Coverage report
./mvnw test jacoco:report
open target/site/jacoco/index.html
```

## Docker

```bash
# Build
docker build -t fsamp-gateway .

# Run with LocalStack
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 \
  fsamp-gateway
```

## Documentation

- **[Architecture Guide](docs/ARCHITECTURE.md)** - Deep dive into system design
- **[API Docs](http://localhost:8080/swagger-ui.html)** - OpenAPI/Swagger (when running)
- **[Event Schema](schema/event.schema.json)** - JSON Schema for domain events

## Security

### Enterprise Controls (FedRAMP-aligned)

- **FedRAMP Moderate-aligned** baseline (not FedRAMP authorized).
- **FIPS 140-3-oriented cryptography** via ACCP + BC-FIPS and AWS KMS for data protection.
- **Audit & monitoring** with structured logging, CloudWatch metrics/alarms, and platform-level CloudTrail.
- **Edge + app security** using WAF, rate limiting, OAuth2/JWT validation, and least-privilege IAM.
- **Network hardening** via private subnets and VPC endpoints for AWS service access.

### Reporting Vulnerabilities

Please report security vulnerabilities to [security@fsamp.io](mailto:security@fsamp.io).

### Compliance

- FIPS 140-3 (cryptography)
- OWASP Top 10 (secure coding)
- SOC 2-oriented (audit logging)

---

## Related Repositories

| Repository | Description |
|---|---|
| **fsamp-processor** | Python event processor (Lambda / standalone) — file scanning, metadata extraction |
| **fsamp-infra** | Terraform IaC, Docker Compose, e2e tests, load tests |
| **fsamp-event-schema** | Canonical JSON Schema for domain events |
| **fsamp-code-ci** | Reusable GitHub Actions workflows & composite actions |

### Central Compliance Documentation (fsamp-infra)

| Document | Path |
|---|---|
| FedRAMP-aligned SSP | `fsamp-infra/docs/compliance/FEDRAMP_SSP.md` |
| NIST 800-53 Control Matrix | `fsamp-infra/docs/compliance/NIST_800_53_CONTROLS.md` |
| Security Review Notes | `fsamp-infra/docs/compliance/SECURITY_AUDIT_REPORT.md` |
| TLS Architecture | `fsamp-infra/docs/TLS_ARCHITECTURE.md` |
| Architecture Decision Records | `fsamp-infra/docs/adr/` |

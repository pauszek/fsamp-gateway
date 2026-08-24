# FSAMP Gateway - Architecture Documentation

## Overview

**FSAMP Gateway** is an enterprise-grade, FIPS 140-3-oriented file upload microservice built with Spring Boot 3.5 and Java 21. It serves as the ingress point for the FSAMP (FedRAMP Moderate-aligned Secure AWS Microservices Platform) system, handling secure file uploads, validation, encryption, and event publishing.

---

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Hexagonal Architecture](#hexagonal-architecture)
3. [Domain Model](#domain-model)
4. [Security Features](#security-features)
5. [AWS Integration](#aws-integration)
6. [Resilience Patterns](#resilience-patterns)
7. [API Reference](#api-reference)
8. [Configuration](#configuration)
9. [Deployment](#deployment)
10. [Testing](#testing)

---

## System Architecture

### High-Level Flow

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           FSAMP Gateway - File Upload Flow                       │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌──────────┐     ┌─────────────────┐     ┌───────────────────────────────────┐ │
│  │  Client  │────▶│  API Gateway    │────▶│         FSAMP Gateway             │ │
│  │  (App)   │     │  (ALB/Cognito)  │     │                                   │ │
│  └──────────┘     └─────────────────┘     │  ┌─────────────────────────────┐  │ │
│                          │                │  │   FileUploadRestAdapter     │  │ │
│                          │ JWT Token      │  │   (REST Controller)         │  │ │
│                          ▼                │  └──────────────┬──────────────┘  │ │
│                   ┌──────────────┐        │                 │                  │ │
│                   │   Cognito    │        │                 ▼                  │ │
│                   │  User Pool   │        │  ┌─────────────────────────────┐  │ │
│                   └──────────────┘        │  │  FileUploadDomainService    │  │ │
│                                           │  │  (Business Logic)           │  │ │
│                                           │  └──────────────┬──────────────┘  │ │
│                                           │                 │                  │ │
│                                           │     ┌───────────┼───────────┐     │ │
│                                           │     ▼           ▼           ▼     │ │
│                                           │  ┌─────┐   ┌─────────┐  ┌──────┐  │ │
│                                           │  │ S3  │   │   SNS   │  │Dynamo│  │ │
│                                           │  │+KMS │   │ Events  │  │  DB  │  │ │
│                                           │  └─────┘   └─────────┘  └──────┘  │ │
│                                           └───────────────────────────────────┘ │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                           │
                                           ▼
                              ┌─────────────────────────┐
                              │     FSAMP Processor     │
                              │   (Malware Scanning)    │
                              └─────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **FileUploadRestAdapter** | REST API endpoint, request validation, rate limiting |
| **FileUploadDomainService** | Orchestrates upload workflow, coordinates ports |
| **TikaContentValidatorAdapter** | MIME type detection, content validation |
| **S3StorageAdapter** | File storage with KMS encryption |
| **SnsEventPublisherAdapter** | Publishes domain events for downstream processing |
| **IdempotencyKeyService** | Prevents duplicate uploads on retries |

---

## Hexagonal Architecture

The project follows **Hexagonal Architecture** (Ports & Adapters) to maintain clean separation between business logic and infrastructure.

### Package Structure

```text
src/main/java/io/github/pauszek/fsampgateway/
├── FsampGatewayApplication.java     # Spring Boot entry point
│
├── adapter/                          # Infrastructure Layer (Adapters)
│   ├── in/                          # Driving Adapters (Primary)
│   │   ├── web/                     # REST Controllers
│   │   │   ├── FileUploadRestAdapter.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── messaging/               # Message Listeners (future)
│   │
│   └── out/                         # Driven Adapters (Secondary)
│       ├── crypto/                  # Content validation (Tika)
│       │   └── TikaContentValidatorAdapter.java
│       ├── messaging/               # SNS Publisher
│       │   └── SnsEventPublisherAdapter.java
│       ├── persistence/             # DynamoDB Repository
│       │   └── InMemoryFileRepositoryAdapter.java
│       └── storage/                 # S3 Storage
│           └── S3StorageAdapter.java
│
├── application/                      # Application Layer
│   ├── dto/                         # Data Transfer Objects
│   │   ├── FileUploadRequestDto.java
│   │   └── FileUploadResponseDto.java
│   ├── mapper/                      # DTO <-> Domain mappers
│   │   └── FileMapper.java
│   └── usecase/                     # Use case implementations
│
├── domain/                           # Domain Layer (Core)
│   ├── command/                     # Command objects
│   │   └── UploadFileCommand.java
│   ├── event/                       # Domain events
│   │   ├── DomainEvent.java
│   │   └── FileUploadedEvent.java
│   ├── exception/                   # Domain exceptions
│   │   ├── FileValidationException.java
│   │   └── StorageException.java
│   ├── model/                       # Domain entities & Value Objects
│   │   ├── SecureFile.java          # Aggregate Root
│   │   ├── FileId.java
│   │   ├── FileName.java
│   │   ├── MimeType.java
│   │   ├── Checksum.java
│   │   └── ...
│   ├── port/                        # Ports (Interfaces)
│   │   ├── in/                      # Driving Ports
│   │   │   ├── UploadFileUseCase.java
│   │   │   └── GetFileUseCase.java
│   │   └── out/                     # Driven Ports
│   │       ├── ContentValidatorPort.java
│   │       ├── FileStoragePort.java
│   │       ├── EventPublisherPort.java
│   │       └── FileRepositoryPort.java
│   └── service/                     # Domain Services
│       └── FileUploadDomainService.java
│
└── infrastructure/                   # Infrastructure Configuration
    ├── config/                      # Spring configurations
    │   ├── AwsConfig.java
    │   ├── FipsCryptoConfig.java
    │   ├── OpenApiConfig.java
    │   └── ResilienceConfig.java
    ├── idempotency/                 # Idempotency implementation
    │   ├── Idempotent.java          # Annotation
    │   ├── IdempotencyAspect.java
    │   └── IdempotencyKeyService.java
    ├── observability/               # Logging, metrics, tracing
    └── security/                    # Security configuration
        ├── SecurityConfig.java
        ├── CorrelationIdFilter.java
        └── cognito/                 # AWS Cognito integration
            └── CognitoJwtConfig.java
```

### Dependency Flow

```text
                    ┌─────────────────────────┐
                    │       Adapters          │
                    │   (Infrastructure)       │
                    └───────────┬─────────────┘
                                │ implements
                                ▼
                    ┌─────────────────────────┐
                    │         Ports           │
                    │      (Interfaces)       │
                    └───────────┬─────────────┘
                                │ used by
                                ▼
                    ┌─────────────────────────┐
                    │        Domain           │
                    │    (Business Logic)     │
                    └─────────────────────────┘
```

**Key Principle**: Dependencies point inward. The domain layer has zero framework dependencies.

---

## Domain Model

### Aggregate Root: SecureFile

```java
SecureFile
├── FileId (UUID)              // Unique identifier
├── CorrelationId              // Request tracing
├── FileName                   // Original filename
├── MimeType                   // Validated content type
├── FileSize                   // Size in bytes
├── Checksum (SHA-256)         // Integrity hash
├── StorageLocation            // S3 bucket + key
├── EncryptionMetadata         // KMS key info
├── FileStatus                 // PENDING -> UPLOADED -> PROCESSING -> COMPLETED
└── AuditInfo                  // Created/updated timestamps, user
```

### File Status State Machine

```text
    ┌─────────┐         ┌──────────┐         ┌────────────┐         ┌───────────┐
    │ PENDING │────────▶│ UPLOADED │────────▶│ PROCESSING │────────▶│ COMPLETED │
    └─────────┘         └──────────┘         └────────────┘         └───────────┘
         │                   │                     │
         │                   │                     │
         ▼                   ▼                     ▼
    ┌─────────┐         ┌─────────┐           ┌─────────┐
    │ FAILED  │         │ FAILED  │           │ FAILED  │
    └─────────┘         └─────────┘           └─────────┘
```

### Domain Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `FILE_UPLOADED` | Successful upload | File metadata, storage location, encryption info |
| `FILE_SCANNED` | Malware scan complete | Scan results (from processor) |
| `ANALYSIS_COMPLETED` | Processing finished | Final status |
| `PROCESSING_FAILED` | Error during processing | Error details |

---

## Security Features

### FIPS 140-3-Oriented Security

The gateway implements a FIPS 140-3-oriented cryptographic posture:

```text
┌───────────────────────────────────────────────────────────────┐
│                    FIPS 140-3 Cryptography                    │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              BouncyCastle FIPS Provider                 │ │
│  │         (NIST CAVP/CMVP Validated)                     │ │
│  └─────────────────────────────────────────────────────────┘ │
│                              │                                │
│          ┌───────────────────┼───────────────────┐           │
│          ▼                   ▼                   ▼           │
│    ┌──────────┐       ┌──────────────┐    ┌───────────┐     │
│    │ SHA-256  │       │  AES-256-GCM │    │   HMAC    │     │
│    │ Checksum │       │  (via KMS)   │    │   SHA-256 │     │
│    └──────────┘       └──────────────┘    └───────────┘     │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

**Configuration**:

```yaml
security:
  fips:
    approved-only: true
    provider-position: 1
```

### Authentication & Authorization

**OAuth2 Resource Server** with AWS Cognito:

```text
┌──────────────┐      ┌───────────────┐      ┌─────────────────┐
│    Client    │─────▶│ AWS Cognito   │─────▶│  FSAMP Gateway  │
│              │      │  (IdP)        │      │                 │
└──────────────┘      └───────────────┘      └─────────────────┘
       │                     │                       │
       │ 1. Login            │                       │
       │────────────────────▶│                       │
       │                     │                       │
       │◀────────────────────│ 2. JWT Token          │
       │                     │                       │
       │ 3. API Request + Bearer Token               │
       │────────────────────────────────────────────▶│
       │                                             │
       │                     │ 4. Validate JWT       │
       │                     │◀──────────────────────│
       │                     │                       │
       │◀────────────────────────────────────────────│ 5. Response
```

**Access Control**:
| Endpoint | Required Permission |
|----------|---------------------|
| `POST /api/v1/files/upload` | `SCOPE_files.write` OR `ROLE_USERS` OR `ROLE_ADMINS` |
| `GET /api/v1/files/{id}` | `SCOPE_files.read` OR `ROLE_USERS` OR `ROLE_ADMINS` |
| `DELETE /api/v1/files/{id}` | `ROLE_ADMINS` only |

### Content Validation (Anti-Spoofing)

**Apache Tika** performs deep content inspection:

```java
// Example: Detecting file type spoofing
// Client declares: image/png
// Actual content: application/x-executable
// Result: REJECTED (potential malware disguise)
```

**Allowed MIME Types**:

- `application/pdf`
- `image/png`, `image/jpeg`
- `application/json`, `application/xml`
- `text/plain`, `text/csv`

---

## AWS Integration

### Services Used

| Service | Purpose |
|---------|---------|
| **S3** | File storage (encrypted at rest with KMS) |
| **KMS** | Envelope encryption (AES-256-GCM) |
| **SNS** | Delivery of outbox events for async processing |
| **DynamoDB** | Idempotency keys, canonical file metadata, transactional outbox |
| **Cognito** | OAuth2 authentication |

### Encryption Flow

```text
┌─────────────────────────────────────────────────────────────┐
│                     KMS Envelope Encryption                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────┐     ┌─────────────────┐     ┌───────────────┐  │
│  │ Client │────▶│  FSAMP Gateway  │────▶│     S3        │  │
│  │  File  │     │  (Plaintext)    │     │ (Ciphertext)  │  │
│  └────────┘     └────────┬────────┘     └───────────────┘  │
│                          │                     ▲            │
│                          │                     │            │
│                          ▼                     │            │
│                 ┌─────────────────┐            │            │
│                 │   AWS KMS       │────────────┘            │
│                 │ (Data Key Gen)  │  Server-Side Encryption │
│                 └─────────────────┘                         │
│                                                              │
│  Encryption: AES-256-GCM with FIPS-oriented runtime controls │
│  Key Management: AWS KMS (FIPS 140-3 Level 3 HSM)           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Event Schema

Events follow a standardized JSON schema for interoperability:

```json
{
  "schemaVersion": "1.2.0",
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "eventId": "123e4567-e89b-42d3-a456-426614174000",
  "correlationId": "123e4567-e89b-12d3-a456-426614174000",
  "timestamp": "2026-01-05T12:00:00.000Z",
  "source": "fsamp-gateway",
  "eventType": "FILE_UPLOADED",
  "fileMetadata": {
    "originalFilename": "document.pdf",
    "fileSizeBytes": 102400,
    "mimeType": "application/pdf",
    "checksumSHA256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  },
  "storageLocation": {
    "bucketName": "fsamp-files",
    "objectKey": "uploads/2026/01/05/550e8400-e29b-41d4-a716-446655440000"
  },
  "securityContext": {
    "isEncrypted": true,
    "encryptionAlgorithm": "AES/GCM/NoPadding",
    "kmsKeyId": "arn:aws:kms:us-west-2:123456789012:key/12345678-1234-1234-1234-123456789012"
  }
}
```

---

## Resilience Patterns

The gateway implements several resilience patterns using **Resilience4j**:

### Circuit Breaker

Prevents cascading failures when downstream services are unavailable:

```text
                   ┌─────────────────────────────────┐
                   │       Circuit Breaker           │
                   │                                 │
  Request ────────▶│  CLOSED ─▶ OPEN ─▶ HALF_OPEN  │────────▶ S3/SNS
                   │                                 │
                   │  50% failure rate -> OPEN       │
                   │  30s wait -> HALF_OPEN          │
                   │  3 success -> CLOSED            │
                   └─────────────────────────────────┘
```

**Configuration**:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      s3Storage:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

### Retry with Exponential Backoff

```text
Attempt 1 ──failed──▶ wait 1s ──▶ Attempt 2 ──failed──▶ wait 2s ──▶ Attempt 3
```

### Rate Limiting

Protects the service from overload:

| Limiter | Rate | Burst |
|---------|------|-------|
| `fileUpload` | 10 req/sec | 25 concurrent |
| `fileDownload` | 50 req/sec | 100 concurrent |

### Bulkhead

Limits concurrent requests to prevent resource exhaustion:

```yaml
resilience4j:
  bulkhead:
    instances:
      fileUpload:
        max-concurrent-calls: 25
        max-wait-duration: 0ms
```

---

## API Reference

### OpenAPI Documentation

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

### Endpoints

#### Upload File

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data
Authorization: Bearer <JWT>
X-Idempotency-Key: <optional-uuid>

file: <binary>
correlationId: <optional-trace-id>
```

**Response** (`201 Created`):

```json
{
  "fileId": "550e8400-e29b-41d4-a716-446655440000",
  "correlationId": "123e4567-e89b-12d3-a456-426614174000",
  "filename": "document.pdf",
  "sizeBytes": 102400,
  "mimeType": "application/pdf",
  "status": "UPLOADED",
  "uploadedAt": "2026-01-05T12:00:00.000Z",
  "message": "File uploaded successfully and queued for processing"
}
```

#### Get File Metadata

```http
GET /api/v1/files/{fileId}
Authorization: Bearer <JWT>
```

#### Health Check

```http
GET /actuator/health
```

### Error Responses

| Status | Description |
|--------|-------------|
| `400` | Validation error (size, type) |
| `401` | Missing or invalid JWT |
| `403` | Insufficient permissions |
| `409` | Idempotency key conflict |
| `413` | File exceeds the deployment limit (9MiB through API Gateway) |
| `415` | Unsupported media type |
| `429` | Rate limit exceeded |
| `503` | Service unavailable (circuit open) |

---

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile (`local`, `dev`, `prod`) | `local` |
| `AWS_REGION` | AWS region | `us-west-2` |
| `S3_BUCKET_NAME` | S3 bucket for files | `fsamp-files-local` |
| `KMS_KEY_ID` | KMS key ID/alias | `alias/fsamp-files-key` |
| `SNS_TOPIC_ARN` | SNS topic for events | - |
| `COGNITO_USER_POOL_ID` | Cognito User Pool ID | - |
| `COGNITO_CLIENT_ID` | Cognito App Client ID | - |
| `FIPS_APPROVED_ONLY` | Enforce FIPS-only algorithms | `true` |

### Profiles

| Profile | Purpose |
|---------|---------|
| `local` | LocalStack development, FIPS disabled |
| `dev` | AWS development environment |
| `prod` | Production with FIPS endpoints and fail-closed crypto posture |

---

## Deployment

### Docker

```bash
# Build
docker build -t fsamp-gateway .

# Run with LocalStack
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=local \
  -e AWS_ENDPOINT_URL=http://host.docker.internal:4566 \
  fsamp-gateway
```

### Kubernetes/ECS

The Docker image includes:

- Non-root user (`fsamp:1001`)
- Health checks
- JVM tuning for containers (`-XX:+UseContainerSupport`)
- FIPS mode enabled by default

---

## Testing

### Unit Tests

```bash
./mvnw test
```

### Integration Tests (Testcontainers)

```bash
./mvnw verify -P integration-tests
```

### Coverage Report

```bash
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html
```

### Architecture Tests (ArchUnit)

Enforces architectural constraints:

- Domain layer has no Spring dependencies
- Adapters don't directly depend on each other
- Cyclic dependencies are prohibited

---

## Observability

### Metrics (Micrometer)

- `file.upload.time` - Upload latency histogram
- `resilience4j.circuitbreaker.*` - Circuit breaker state
- `resilience4j.ratelimiter.*` - Rate limiter stats

### Logging

Structured logging with correlation ID:

```text
2026-01-05 12:00:00.000 [http-nio-8080-exec-1] [123e4567-e89b-12d3-a456-426614174000] INFO FileUploadDomainService - File upload completed: fileId=550e8400, status=UPLOADED
```

### Management Endpoints

- `/actuator/health` - Health status
- `/actuator/info` - Build information

Every other actuator path is denied by the security filter chain; runtime metrics
are exported to CloudWatch.

---

## References

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)
- [FIPS 140-3 Standard](https://csrc.nist.gov/publications/detail/fips/140/3/final)
- [BouncyCastle FIPS](https://www.bouncycastle.org/fips-java/)
- [Resilience4j](https://resilience4j.readme.io/)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)

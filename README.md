# FSAMP Gateway

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![FIPS 140-3](https://img.shields.io/badge/FIPS-140--3-blue)](https://csrc.nist.gov/publications/detail/fips/140/3/final)

> Secure file upload gateway for the FSAMP platform with FIPS 140-3 compliant encryption.

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker (for LocalStack)
- LocalStack running (see fsamp-infra)

### Run Locally

```bash
# Start LocalStack first (from fsamp-infra)
cd ../fsamp-infra && make up && make apply-local

# Run the gateway
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
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

## 📡 API Endpoints

### Upload File

```bash
POST /api/v1/files/upload
Content-Type: multipart/form-data

curl -X POST http://localhost:8080/api/v1/files/upload \
  -F "file=@document.pdf" \
  -F "correlationId=my-trace-123"
```

**Response:**
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

### Health Check

```bash
GET /actuator/health
GET /api/v1/health
GET /api/v1/info
```

## 🔐 Security Features

- **FIPS 140-3 Compliance**: BouncyCastle FIPS provider for cryptographic operations
- **Server-Side Encryption**: All files encrypted with AWS KMS (AES-256-GCM)
- **Content Validation**: Apache Tika-based MIME type detection (anti-spoofing)
- **Checksum Verification**: SHA-256 hash for integrity validation

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        File Upload Flow                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Client                                                          │
│    │                                                             │
│    ▼                                                             │
│  ┌──────────────────────┐                                       │
│  │  FileUploadController │  ← REST API                          │
│  └──────────┬───────────┘                                       │
│             │                                                    │
│             ▼                                                    │
│  ┌──────────────────────┐                                       │
│  │  FileValidationSvc   │  ← Size, MIME type (Tika)            │
│  └──────────┬───────────┘                                       │
│             │                                                    │
│             ▼                                                    │
│  ┌──────────────────────┐     ┌─────────────────┐               │
│  │  FileUploadService   │────▶│  CryptoService  │ (SHA-256)    │
│  └──────────┬───────────┘     └─────────────────┘               │
│             │                                                    │
│     ┌───────┴───────┐                                           │
│     ▼               ▼                                            │
│  ┌──────┐     ┌──────────┐                                      │
│  │  S3  │     │   SNS    │                                      │
│  │ (KMS)│     │ (events) │                                      │
│  └──────┘     └──────────┘                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## 📁 Project Structure

```
src/main/java/io/github/pauszek/fsampgateway/
├── config/           # AWS & security configuration
├── controller/       # REST endpoints
├── domain/           # Domain entities (FileMetadata, FsampEvent)
├── dto/              # Request/Response DTOs
├── exception/        # Custom exceptions & global handler
└── service/          # Business logic (upload, S3, SNS, crypto)
```

## ⚙️ Configuration

### Environment Variables (AWS)

| Variable | Description |
|----------|-------------|
| `SPRING_PROFILES_ACTIVE` | `local`, `dev`, or `prod` |
| `AWS_REGION` | AWS region |
| `SNS_FILE_EVENTS_TOPIC_ARN` | SNS topic for events |
| `KMS_KEY_ID` | KMS key for encryption |

### LocalStack (Development)

The `local` profile automatically configures:
- Endpoint: `http://localhost:4566`
- Credentials: `test/test`
- Bucket: `fsamp-local-files`

## 🧪 Testing

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify -P integration-tests

# With coverage
./mvnw test jacoco:report
```

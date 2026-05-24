package io.github.pauszek.fsampgateway.domain.model;

import java.util.Objects;

public final class SecureFile {

    private final FileId id;
    private final CorrelationId correlationId;
    private final FileName fileName;
    private final MimeType mimeType;
    private final FileSize size;
    private final Checksum checksum;
    private final StorageLocation storageLocation;
    private final EncryptionMetadata encryptionMetadata;
    private final FileStatus status;
    private final AuditInfo auditInfo;

    private SecureFile(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "File ID is required");
        this.correlationId = Objects.requireNonNull(builder.correlationId, "Correlation ID is required");
        this.fileName = Objects.requireNonNull(builder.fileName, "File name is required");
        this.mimeType = Objects.requireNonNull(builder.mimeType, "MIME type is required");
        this.size = Objects.requireNonNull(builder.size, "File size is required");
        this.checksum = builder.checksum;
        this.storageLocation = builder.storageLocation;
        this.encryptionMetadata = builder.encryptionMetadata;
        this.status = Objects.requireNonNull(builder.status, "Status is required");
        this.auditInfo = Objects.requireNonNull(builder.auditInfo, "Audit info is required");
    }

    public static SecureFile createPending(
            FileName fileName,
            MimeType mimeType,
            FileSize size,
            CorrelationId correlationId,
            String uploadedBy
    ) {
        return builder()
                .id(FileId.generate())
                .correlationId(correlationId)
                .fileName(fileName)
                .mimeType(mimeType)
                .size(size)
                .status(FileStatus.PENDING)
                .auditInfo(AuditInfo.create(uploadedBy))
                .build();
    }

    public SecureFile markAsUploaded(
            StorageLocation storageLocation,
            EncryptionMetadata encryptionMetadata,
            Checksum checksum
    ) {
        if (this.status != FileStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark file as uploaded from status: " + this.status);
        }
        
        return toBuilder()
                .storageLocation(storageLocation)
                .encryptionMetadata(encryptionMetadata)
                .checksum(checksum)
                .status(FileStatus.UPLOADED)
                .auditInfo(auditInfo.update())
                .build();
    }

    public SecureFile markAsProcessing() {
        if (this.status != FileStatus.UPLOADED) {
            throw new IllegalStateException(
                    "Cannot start processing from status: " + this.status);
        }
        
        return toBuilder()
                .status(FileStatus.PROCESSING)
                .auditInfo(auditInfo.update())
                .build();
    }

    public SecureFile markAsCompleted() {
        if (this.status != FileStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Cannot complete from status: " + this.status);
        }
        
        return toBuilder()
                .status(FileStatus.COMPLETED)
                .auditInfo(auditInfo.update())
                .build();
    }

    public SecureFile markAsFailed() {
        return toBuilder()
                .status(FileStatus.FAILED)
                .auditInfo(auditInfo.update())
                .build();
    }

    public FileId getId() { return id; }
    public CorrelationId getCorrelationId() { return correlationId; }
    public FileName getFileName() { return fileName; }
    public MimeType getMimeType() { return mimeType; }
    public FileSize getSize() { return size; }
    public Checksum getChecksum() { return checksum; }
    public StorageLocation getStorageLocation() { return storageLocation; }
    public EncryptionMetadata getEncryptionMetadata() { return encryptionMetadata; }
    public FileStatus getStatus() { return status; }
    public AuditInfo getAuditInfo() { return auditInfo; }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .correlationId(this.correlationId)
                .fileName(this.fileName)
                .mimeType(this.mimeType)
                .size(this.size)
                .checksum(this.checksum)
                .storageLocation(this.storageLocation)
                .encryptionMetadata(this.encryptionMetadata)
                .status(this.status)
                .auditInfo(this.auditInfo);
    }

    public static final class Builder {
        private FileId id;
        private CorrelationId correlationId;
        private FileName fileName;
        private MimeType mimeType;
        private FileSize size;
        private Checksum checksum;
        private StorageLocation storageLocation;
        private EncryptionMetadata encryptionMetadata;
        private FileStatus status;
        private AuditInfo auditInfo;

        public Builder id(FileId id) { this.id = id; return this; }
        public Builder correlationId(CorrelationId correlationId) { this.correlationId = correlationId; return this; }
        public Builder fileName(FileName fileName) { this.fileName = fileName; return this; }
        public Builder mimeType(MimeType mimeType) { this.mimeType = mimeType; return this; }
        public Builder size(FileSize size) { this.size = size; return this; }
        public Builder checksum(Checksum checksum) { this.checksum = checksum; return this; }
        public Builder storageLocation(StorageLocation storageLocation) { this.storageLocation = storageLocation; return this; }
        public Builder encryptionMetadata(EncryptionMetadata encryptionMetadata) { this.encryptionMetadata = encryptionMetadata; return this; }
        public Builder status(FileStatus status) { this.status = status; return this; }
        public Builder auditInfo(AuditInfo auditInfo) { this.auditInfo = auditInfo; return this; }

        public SecureFile build() {
            return new SecureFile(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecureFile that = (SecureFile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SecureFile{" +
                "id=" + id +
                ", fileName=" + fileName +
                ", status=" + status +
                '}';
    }
}

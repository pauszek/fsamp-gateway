package io.github.pauszek.fsampgateway.domain.model;

public enum FileStatus {

    PENDING("pending", "File received, awaiting validation"),

    UPLOADED("uploaded", "File stored, awaiting processing"),

    SCANNING("scanning", "Security scan in progress"),

    PROCESSING("processing", "File analysis in progress"),

    COMPLETED("completed", "Processing completed"),

    DELETING("deleting", "Deletion in progress"),

    FAILED("failed", "Processing failed");

    private final String code;
    private final String description;

    FileStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public boolean canRetry() {
        return this == FAILED;
    }

    public static FileStatus fromCode(String code) {
        for (FileStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}

package io.github.pauszek.fsampgateway.domain.model;

/**
 * Enum - File Processing Status.
 * 
 * Represents the lifecycle states of a file in the system.
 */
public enum FileStatus {
    
    /**
     * File received, validation pending.
     */
    PENDING("pending", "File received, awaiting validation"),
    
    /**
     * File uploaded to storage successfully.
     */
    UPLOADED("uploaded", "File stored, awaiting processing"),
    
    /**
     * File is being scanned for malware.
     */
    SCANNING("scanning", "Security scan in progress"),
    
    /**
     * File is being processed/analyzed.
     */
    PROCESSING("processing", "File analysis in progress"),
    
    /**
     * Processing completed successfully.
     */
    COMPLETED("completed", "Processing completed"),
    
    /**
     * Processing failed - see error details.
     */
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

    /**
     * Check if this is a terminal state (no more transitions).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    /**
     * Check if this state allows retry.
     */
    public boolean canRetry() {
        return this == FAILED;
    }

    /**
     * Get status from code string.
     */
    public static FileStatus fromCode(String code) {
        for (FileStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}

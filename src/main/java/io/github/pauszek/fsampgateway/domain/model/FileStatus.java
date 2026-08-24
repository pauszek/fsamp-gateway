package io.github.pauszek.fsampgateway.domain.model;

public enum FileStatus {

    PENDING("File received, awaiting validation"),

    UPLOADED("File stored, awaiting processing"),

    SCANNING("Security scan in progress"),

    PROCESSING("File analysis in progress"),

    COMPLETED("Processing completed"),

    DELETING("Deletion in progress"),

    FAILED("Processing failed");

    private final String description;

    FileStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

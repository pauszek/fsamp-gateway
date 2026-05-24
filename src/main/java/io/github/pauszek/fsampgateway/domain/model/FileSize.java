package io.github.pauszek.fsampgateway.domain.model;

public record FileSize(long bytes) {

    public static final long MAX_SIZE = 100 * 1024 * 1024; // 100 MB
    public static final long MIN_SIZE = 1; // 1 byte

    public FileSize {
        if (bytes < MIN_SIZE) {
            throw new IllegalArgumentException("File size must be at least " + MIN_SIZE + " byte");
        }
        if (bytes > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum of " + formatBytes(MAX_SIZE));
        }
    }

    public static FileSize of(long bytes) {
        return new FileSize(bytes);
    }

    public static FileSize ofWithLimit(long bytes, long maxBytes) {
        if (bytes > maxBytes) {
            throw new IllegalArgumentException(
                    "File size " + formatBytes(bytes) + " exceeds limit of " + formatBytes(maxBytes));
        }
        return new FileSize(bytes);
    }

    public double toKilobytes() {
        return bytes / 1024.0;
    }

    public double toMegabytes() {
        return bytes / (1024.0 * 1024.0);
    }

    public String toHumanReadable() {
        return formatBytes(bytes);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    @Override
    public String toString() {
        return toHumanReadable();
    }
}

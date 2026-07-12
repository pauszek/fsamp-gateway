package io.github.pauszek.fsampgateway.domain.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class StoragePayload {

    private final String bucketName;
    private final String objectKey;
    private final String region;

    private StoragePayload(String bucketName, String objectKey, String region) {
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.region = region;
    }

    public static StoragePayload of(String bucketName, String objectKey) {
        return new StoragePayload(bucketName, objectKey, null);
    }

    public static StoragePayload of(String bucketName, String objectKey, String region) {
        return new StoragePayload(bucketName, objectKey, region);
    }
}

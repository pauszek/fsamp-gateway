package io.github.pauszek.fsampgateway.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(staticName = "of")
public final class SecurityPayload {

    @JsonProperty("isEncrypted")
    private final boolean encrypted;

    private final String encryptionAlgorithm;

    private final String kmsKeyId;
}

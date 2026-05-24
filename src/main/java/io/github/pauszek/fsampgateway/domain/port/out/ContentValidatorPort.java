package io.github.pauszek.fsampgateway.domain.port.out;

import io.github.pauszek.fsampgateway.domain.model.Checksum;
import io.github.pauszek.fsampgateway.domain.model.MimeType;
import io.github.pauszek.fsampgateway.domain.model.ValidationResult;

import java.io.InputStream;

public interface ContentValidatorPort {

    MimeType detectMimeType(InputStream content, String fileName);

    ValidationResult validate(InputStream content, MimeType declaredType, String fileName);

    Checksum computeChecksum(InputStream content);
}

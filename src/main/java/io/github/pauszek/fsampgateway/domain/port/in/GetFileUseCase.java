package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.model.FileId;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

import java.util.Optional;

public interface GetFileUseCase {

    Optional<SecureFile> getById(FileId fileId);

    SecureFile getByIdOrThrow(FileId fileId);
}

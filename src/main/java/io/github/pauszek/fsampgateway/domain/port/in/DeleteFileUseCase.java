package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.model.FileId;

public interface DeleteFileUseCase {

    void execute(FileId fileId);
}

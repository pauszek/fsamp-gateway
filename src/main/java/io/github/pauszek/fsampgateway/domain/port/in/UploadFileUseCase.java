package io.github.pauszek.fsampgateway.domain.port.in;

import io.github.pauszek.fsampgateway.domain.command.UploadFileCommand;
import io.github.pauszek.fsampgateway.domain.model.SecureFile;

public interface UploadFileUseCase {

    SecureFile execute(UploadFileCommand command);
}

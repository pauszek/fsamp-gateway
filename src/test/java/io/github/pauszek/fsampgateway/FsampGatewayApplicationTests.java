package io.github.pauszek.fsampgateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Application context test.
 * 
 * Note: This test requires LocalStack running or proper AWS credentials.
 * Disabled by default in CI pipelines without infrastructure.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration"
})
@DisabledIfSystemProperty(named = "skipIntegrationTests", matches = "true")
class FsampGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Will be enabled when LocalStack is configured for tests
    }

}

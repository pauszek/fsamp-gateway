package io.github.pauszek.fsampgateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration"
})
@DisabledIfSystemProperty(named = "skipIntegrationTests", matches = "true")
class FsampGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

}

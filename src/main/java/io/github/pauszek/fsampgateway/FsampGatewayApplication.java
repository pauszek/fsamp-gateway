package io.github.pauszek.fsampgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FsampGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FsampGatewayApplication.class, args);
    }

}

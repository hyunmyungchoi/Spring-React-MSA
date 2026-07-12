package com.springmsa.memberstockservice.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void actuatorProbeHealthGroupsArePublic() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/springmsa/memberstockservice/config/SecurityConfig.java"
        ));

        assertThat(source).contains(".requestMatchers(\"/actuator/health/**\").permitAll()");
    }
}

package com.springmsa.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptGenerateTest {

    @Test
    void generateBffClientSecret() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String encoded = encoder.encode("bff-secret");

        System.out.println();
        System.out.println("========================================");
        System.out.println("BCrypt bff-client secret:");
        System.out.println(encoded);
        System.out.println("========================================");
        System.out.println();
    }
}
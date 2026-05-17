package com.springmsa.userservice.init;

import com.springmsa.userservice.domain.User;
import com.springmsa.userservice.repository.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class TestUserInitializer {

    @Bean
    public ApplicationRunner initTestUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.existsByLoginId("user")) {
                return;
            }

            User user = User.create(
                    "user",
                    "user@test.com",
                    passwordEncoder.encode("password"),
                    "Test User",
                    "01012345678",
                    "01012345678",
                    Set.of("ROLE_USER")
            );

            userRepository.save(user);
        };
    }
}
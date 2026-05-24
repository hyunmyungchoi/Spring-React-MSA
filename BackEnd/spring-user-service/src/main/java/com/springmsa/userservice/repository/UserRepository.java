package com.springmsa.userservice.repository;

import com.springmsa.userservice.domain.User;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<@NonNull User, @NonNull Long> {

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByWhatsappNumber(String whatsappNumber);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
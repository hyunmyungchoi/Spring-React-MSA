package com.springmsa.userservice.user.repository;

import com.springmsa.userservice.user.domain.User;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<@NonNull User, @NonNull Long> {

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByWhatsappNumber(String whatsappNumber);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}

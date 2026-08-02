package com.springmsa.userservice.user.service;

import com.springmsa.userservice.user.domain.User;
import com.springmsa.userservice.user.dto.CreateUserRequest;
import com.springmsa.userservice.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserCreateServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    InternalUserCreateService service;

    @BeforeEach
    void setUp() {
        service = new InternalUserCreateService(userRepository, passwordEncoder);
    }

    @Test
    void rejectsAdminRoleCreationThroughTheInternalApi() {
        CreateUserRequest request = request(Set.of("ROLE_USER", "ROLE_ADMIN"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("Unsupported role: ROLE_ADMIN");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void createsAnOrdinaryUserWithOnlyTheUserRole() {
        when(userRepository.existsByLoginId("member1")).thenReturn(false);
        when(userRepository.existsByEmail("member1@example.com")).thenReturn(false);
        when(passwordEncoder.encode("safe-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request(null));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly("ROLE_USER");
        assertThat(response.roles()).containsExactly("ROLE_USER");
    }

    private static CreateUserRequest request(Set<String> roles) {
        return new CreateUserRequest(
                "member1",
                "member1@example.com",
                "safe-password",
                "Member One",
                null,
                null,
                roles
        );
    }
}

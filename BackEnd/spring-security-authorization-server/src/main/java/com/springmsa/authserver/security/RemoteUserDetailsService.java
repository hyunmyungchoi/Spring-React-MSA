package com.springmsa.authserver.security;

import com.springmsa.authserver.client.UserServiceClient;
import com.springmsa.authserver.client.dto.AuthUserResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RemoteUserDetailsService implements UserDetailsService {

    private final UserServiceClient userServiceClient;

    public RemoteUserDetailsService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            AuthUserResponse authUser = userServiceClient.findAuthUserByLoginId(username);

            return User.withUsername(authUser.loginId())
                    .password(authUser.password())
                    .authorities(
                            authUser.roles().stream()
                                    .map(SimpleGrantedAuthority::new)
                                    .toList()
                    )
                    .disabled(!authUser.enabled())
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .build();

        } catch (ResponseStatusException e) {
            throw new UsernameNotFoundException("User not found: " + username, e);
        }
    }
}
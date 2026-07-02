package com.springmsa.adminbff.auth;

import com.springmsa.adminbff.auth.dto.AdminSessionUserResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminPrincipalClaimsMapper {

    public boolean hasRole(Authentication authentication, String role) {
        Map<String, Object> attributes = getUserAttributes(authentication);

        if (hasRoleValue(attributes.get("roles"), role)) {
            return true;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    public AdminSessionUserResponse toSessionUser(Authentication authentication) {
        Map<String, Object> attributes = getUserAttributes(authentication);

        return new AdminSessionUserResponse(
                stringClaim(attributes.get("sub")),
                stringClaim(attributes.get("name")),
                longClaim(firstClaim(attributes, "userId", "user_id")),
                stringClaim(firstClaim(attributes, "loginId", "login_id")),
                stringClaim(attributes.get("email")),
                rolesClaim(attributes.get("roles"))
        );
    }

    private Map<String, Object> getUserAttributes(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            return new LinkedHashMap<>(oidcUser.getClaims());
        }

        if (principal instanceof OAuth2User oauth2User) {
            return new LinkedHashMap<>(oauth2User.getAttributes());
        }

        return new LinkedHashMap<>();
    }

    private boolean hasRoleValue(Object roles, String role) {
        if (roles instanceof Collection<?> roleValues) {
            return roleValues.stream()
                    .map(String::valueOf)
                    .anyMatch(role::equals);
        }

        return role.equals(String.valueOf(roles));
    }

    private Object firstClaim(Map<String, Object> attributes, String... names) {
        for (String name : names) {
            Object value = attributes.get(name);

            if (value != null) {
                return value;
            }
        }

        return null;
    }

    private String stringClaim(Object value) {
        if (value == null) {
            return null;
        }

        String stringValue = String.valueOf(value);

        if (stringValue.isBlank()) {
            return null;
        }

        return stringValue;
    }

    private Long longClaim(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        String stringValue = stringClaim(value);

        if (stringValue == null) {
            return null;
        }

        try {
            return Long.parseLong(stringValue);

        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> rolesClaim(Object roles) {
        if (roles instanceof Collection<?> roleValues) {
            return roleValues.stream()
                    .map(String::valueOf)
                    .filter(role -> !role.isBlank())
                    .toList();
        }

        String role = stringClaim(roles);

        if (role == null) {
            return List.of();
        }

        if (role.contains(",")) {
            List<String> values = new ArrayList<>();

            for (String value : role.split(",")) {
                String trimmedValue = value.trim();

                if (!trimmedValue.isBlank()) {
                    values.add(trimmedValue);
                }
            }

            return List.copyOf(values);
        }

        return List.of(role);
    }
}

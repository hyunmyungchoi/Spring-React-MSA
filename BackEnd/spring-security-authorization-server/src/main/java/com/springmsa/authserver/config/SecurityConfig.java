package com.springmsa.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/oauth2/**",
                        "/.well-known/**",
                        "/userinfo",
                        "/connect/**"
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                ).exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                        )
                )
                .oauth2AuthorizationServer(authorizationServer -> authorizationServer
                        .oidc(oidc -> oidc
                                .userInfoEndpoint(userInfo -> userInfo
                                        .userInfoMapper(userInfoMapper())
                                )
                        )
                );

        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain appSecurityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/login/whatsapp/**", "/login/email/**", "/webhooks/whatsapp")
                )
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login/whatsapp/**", "/login/email/**").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/webhooks/whatsapp").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                );


        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
        return context -> {
            Map<String, Object> claims = getStringObjectMap(context);

            Map<String, Object> userInfo = new LinkedHashMap<>();
            putIfNotNull(userInfo, "sub", claims.get("sub"));
            putIfNotNull(userInfo, "name", claims.get("name"));
            putIfNotNull(userInfo, "userId", claims.get("user_id"));
            putIfNotNull(userInfo, "loginId", claims.get("login_id"));
            putIfNotNull(userInfo, "email", claims.get("email"));
            putIfNotNull(userInfo, "roles", claims.get("roles"));

            return new OidcUserInfo(userInfo);
        };
    }

    private static Map<String, Object> getStringObjectMap(OidcUserInfoAuthenticationContext context) {
        OidcUserInfoAuthenticationToken authentication = context.getAuthentication();

        Object principalObject = authentication.getPrincipal();

        if (!(principalObject instanceof JwtAuthenticationToken principal)) {
            throw new IllegalStateException("OIDC UserInfo principal must be JwtAuthenticationToken");
        }

        Jwt jwt = principal.getToken();

        if (jwt == null) {
            throw new IllegalStateException("OIDC UserInfo JWT must not be null");
        }

        return jwt.getClaims();
    }

    private void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
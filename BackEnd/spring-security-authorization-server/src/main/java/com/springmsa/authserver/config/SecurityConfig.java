package com.springmsa.authserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Authorization Protocol (Oauth2 / OIDC)
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Handles OAuth2/OIDC protocol endpoints before the application login chain.
        LoginUrlAuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint("/login");

        http
                .securityMatcher(
                        "/oauth2/**",           // Authorization, token, JWK, and related OAuth2 endpoints.
                        "/.well-known/**",      // OIDC discovery metadata.
                        "/connect/**",          // OIDC provider endpoints such as logout.
                        "/userinfo"             // BFFs call this with an access token to load user claims.
                )
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                ).exceptionHandling(exceptions -> exceptions
                        // Redirect unauthenticated protocol requests to /login regardless of Accept headers.
                        .authenticationEntryPoint(loginEntryPoint)
                )
                .oauth2AuthorizationServer(authorizationServer -> authorizationServer
                        // Enables OIDC support, including the /userinfo endpoint.
                        .oidc(oidc -> oidc
                                .userInfoEndpoint(userInfo -> userInfo
                                        // Controls which JWT claims are exposed from /userinfo.
                                        .userInfoMapper(userInfoMapper())
                                )
                        )
                );

        return http.build();
    }


    /**
     * Application Protocol (API, Login, Logout...)
     */
    @Bean
    @Order(2)
    SecurityFilterChain appSecurityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/login/**",
                                "/webhooks/whatsapp"
                        )
                )
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/login/**", "/error").permitAll()
                        .requestMatchers("/webhooks/whatsapp").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new RegexRequestMatcher("^/logout(\\?.*)?$", null))
                        .logoutSuccessHandler((request, response, authentication) -> {
                            String redirectUri = request.getParameter("post_logout_redirect_uri");

                            response.sendRedirect(
                                    StringUtils.hasText(redirectUri)
                                            ? redirectUri
                                            : "/login?logout"
                            );
                        })
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
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

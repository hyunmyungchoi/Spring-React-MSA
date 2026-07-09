package com.springmsa.authserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
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
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String LOGIN_PAGE = "/login";
    private static final String LOGOUT_ENDPOINT = "/logout";
    private static final String AUTH_SESSION_COOKIE = "AUTHSESSIONID";
    private static final String POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";
    private static final String DEFAULT_LOGOUT_REDIRECT_URI = LOGIN_PAGE + "?logout";

    @Value("${app.frontend.user-login-uri:}")
    private String userFrontendLoginUri;

    @Value("${app.frontend.admin-login-uri:}")
    private String adminFrontendLoginUri;

    /**
     * Authorization Protocol (Oauth2 / OIDC)
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        // Handles OAuth2/OIDC protocol endpoints before the application login chain.
        LoginUrlAuthenticationEntryPoint loginEntryPoint = new LoginUrlAuthenticationEntryPoint(LOGIN_PAGE);

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
                                pathPattern(HttpMethod.POST, "/login/**")
                        )
                )
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(LOGIN_PAGE, "/login/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage(LOGIN_PAGE)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(pathPattern(HttpMethod.GET, LOGOUT_ENDPOINT))
                        .logoutSuccessHandler(this::handleLogoutSuccess)
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies(AUTH_SESSION_COOKIE)
                );


        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private void handleLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        response.sendRedirect(resolvePostLogoutRedirectUri(request.getParameter(POST_LOGOUT_REDIRECT_URI)));
    }

    private String resolvePostLogoutRedirectUri(String requestedRedirectUri) {
        URI requestedUri = parseAbsoluteUri(requestedRedirectUri);

        if (requestedUri == null || !isAllowedPostLogoutRedirectUri(requestedUri)) {
            return DEFAULT_LOGOUT_REDIRECT_URI;
        }

        return requestedUri.toString();
    }

    private boolean isAllowedPostLogoutRedirectUri(URI requestedUri) {
        return Stream.of(userFrontendLoginUri, adminFrontendLoginUri)
                .map(this::parseAbsoluteUri)
                .anyMatch(requestedUri::equals);
    }

    private URI parseAbsoluteUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return null;
        }

        try {
            URI parsedUri = URI.create(uri.trim()).normalize();

            if (!parsedUri.isAbsolute() || !StringUtils.hasText(parsedUri.getHost()) || parsedUri.getRawUserInfo() != null) {
                return null;
            }

            return parsedUri;

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper() {
        return context -> {
            Map<String, Object> claims = getStringObjectMap(context);

            Map<String, Object> userInfo = new LinkedHashMap<>();
            putIfNotNull(userInfo, "sub", claims.get("sub"));
            putIfNotNull(userInfo, "name", claims.get("name"));
            putIfNotNull(userInfo, "username", claims.get("username"));
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

package com.springmsa.memberbff;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndpointTest {

    private static final String EXCLUDED_AUTO_CONFIGURATIONS = String.join(",",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
            "org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration"
    );

    @Test
    void servesPrometheusMetricsOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(PrometheusTestApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);

        try (ConfigurableApplicationContext context = application.run(
                "--server.port=0",
                "--spring.application.name=spring-member-bff-service",
                "--spring.autoconfigure.exclude=" + EXCLUDED_AUTO_CONFIGURATIONS,
                "--management.endpoints.web.exposure.include=prometheus"
        )) {
            context.getBean(MeterRegistry.class)
                    .counter("member_bff_prometheus_contract")
                    .increment();

            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/actuator/prometheus"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("content-type"))
                    .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("text/plain"));
            assertThat(response.body()).contains("member_bff_prometheus_contract_total 1.0");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class PrometheusTestApplication {
    }
}

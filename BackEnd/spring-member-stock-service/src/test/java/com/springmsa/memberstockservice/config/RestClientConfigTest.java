package com.springmsa.memberstockservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    @Test
    void providesRestClientBuilderForTossClients() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(RestClientConfig.class);
            context.refresh();

            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
        }
    }
}

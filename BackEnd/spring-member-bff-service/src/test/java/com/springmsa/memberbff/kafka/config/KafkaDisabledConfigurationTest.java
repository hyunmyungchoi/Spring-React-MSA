package com.springmsa.memberbff.kafka.config;

import com.springmsa.memberbff.chat.kafka.ChatMessageAnalyticsConsumer;
import com.springmsa.memberbff.chat.kafka.ChatMessageDeadLetterConsumer;
import com.springmsa.memberbff.chat.kafka.ChatMessageKafkaEventPublisher;
import com.springmsa.memberbff.chat.kafka.ChatMessageNotificationConsumer;
import com.springmsa.memberbff.auth.service.BffOAuth2ClientService;
import com.springmsa.memberbff.userbff.client.UserServiceClient;
import com.springmsa.memberbff.userbff.service.UserBffService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaDisabledConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaTestConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void kafkaBeansAreNotCreatedWhenKafkaIsDisabled() {
        contextRunner
                .withPropertyValues("app.kafka.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(KafkaAdmin.class);
                    assertThat(context).doesNotHaveBean(KafkaTemplate.class);
                    assertThat(context).doesNotHaveBean(DefaultErrorHandler.class);
                    assertThat(context).doesNotHaveBean(ConcurrentKafkaListenerContainerFactory.class);
                });
    }

    @Test
    void chatKafkaComponentsAreNotCreatedWhenKafkaIsDisabled() {
        contextRunner
                .withPropertyValues("app.kafka.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatMessageKafkaEventPublisher.class);
                    assertThat(context).doesNotHaveBean(ChatMessageNotificationConsumer.class);
                    assertThat(context).doesNotHaveBean(ChatMessageAnalyticsConsumer.class);
                    assertThat(context).doesNotHaveBean(ChatMessageDeadLetterConsumer.class);
                });
    }

    @Test
    void kafkaBeansAreCreatedWhenKafkaIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.kafka.enabled=true",
                        "spring.kafka.bootstrap-servers=localhost:9092"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(KafkaAdmin.class);
                    assertThat(context).hasSingleBean(KafkaTemplate.class);
                    assertThat(context).hasSingleBean(DefaultErrorHandler.class);
                    assertThat(context).hasSingleBean(ConcurrentKafkaListenerContainerFactory.class);
                });
    }

    @Test
    void memberCoreServiceBeanIsCreatedWhenKafkaIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(MemberCoreServiceTestConfiguration.class)
                .withPropertyValues("app.kafka.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(UserBffService.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            KafkaClientConfig.class,
            KafkaRetryConfig.class,
            KafkaTopicConfig.class,
            ChatMessageKafkaEventPublisher.class,
            ChatMessageNotificationConsumer.class,
            ChatMessageAnalyticsConsumer.class,
            ChatMessageDeadLetterConsumer.class
    })
    static class KafkaTestConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(UserBffService.class)
    static class MemberCoreServiceTestConfiguration {

        @Bean
        BffOAuth2ClientService bffOAuth2ClientService() {
            return mock(BffOAuth2ClientService.class);
        }

        @Bean
        UserServiceClient userServiceClient() {
            return mock(UserServiceClient.class);
        }
    }
}

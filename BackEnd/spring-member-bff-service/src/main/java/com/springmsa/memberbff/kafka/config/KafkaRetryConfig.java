package com.springmsa.memberbff.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaRetryConfig {

    @Bean
    DefaultErrorHandler kafkaDefaultErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${bff.kafka.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${bff.kafka.retry.max-attempts:3}") long maxRetryAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition())
        );

        return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, maxRetryAttempts));
    }
}

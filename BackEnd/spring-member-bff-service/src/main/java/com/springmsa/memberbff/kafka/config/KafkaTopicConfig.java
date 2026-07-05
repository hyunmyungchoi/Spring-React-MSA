package com.springmsa.memberbff.kafka.config;

import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaTopicConfig {

    @Bean
    KafkaAdmin.NewTopics springMsaKafkaTopics(
            @Value("${bff.kafka.topics.partitions:3}") int partitions,
            @Value("${bff.kafka.topics.replicas:1}") int replicas
    ) {
        return new KafkaAdmin.NewTopics(
                topic(MsaKafkaTopics.CHAT_MESSAGE_CREATED, partitions, replicas),
                topic(MsaKafkaTopics.CHAT_MESSAGE_CREATED_DLT, partitions, replicas)
        );
    }

    private NewTopic topic(String name, int partitions, int replicas) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}

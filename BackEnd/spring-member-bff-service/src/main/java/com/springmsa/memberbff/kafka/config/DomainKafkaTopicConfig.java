package com.springmsa.memberbff.kafka.config;

import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class DomainKafkaTopicConfig {

    @Bean
    NewTopic communityPostCreatedTopic() {
        return topic(MsaKafkaTopics.COMMUNITY_POST_CREATED_V1);
    }

    @Bean
    NewTopic communityPostCreatedDltTopic() {
        return topic(MsaKafkaTopics.COMMUNITY_POST_CREATED_V1_DLT);
    }

    @Bean
    NewTopic userRegisteredTopic() {
        return topic(MsaKafkaTopics.USER_REGISTERED_V1);
    }

    @Bean
    NewTopic userRegisteredDltTopic() {
        return topic(MsaKafkaTopics.USER_REGISTERED_V1_DLT);
    }

    @Bean
    NewTopic watchlistItemAddedTopic() {
        return topic(MsaKafkaTopics.WATCHLIST_ITEM_ADDED_V1);
    }

    @Bean
    NewTopic watchlistItemAddedDltTopic() {
        return topic(MsaKafkaTopics.WATCHLIST_ITEM_ADDED_V1_DLT);
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }
}

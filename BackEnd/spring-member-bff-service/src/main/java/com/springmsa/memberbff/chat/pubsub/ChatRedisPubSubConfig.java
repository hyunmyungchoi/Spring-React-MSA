package com.springmsa.memberbff.chat.pubsub;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@RequiredArgsConstructor
public class ChatRedisPubSubConfig {

    private final ChatRedisPubSubSubscriber chatRedisPubSubSubscriber;

    @Bean
    RedisMessageListenerContainer chatRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${bff.chat.pubsub.channel:spring:chat:broadcast}") String chatBroadcastChannel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(chatRedisPubSubSubscriber, new ChannelTopic(chatBroadcastChannel));
        return container;
    }
}

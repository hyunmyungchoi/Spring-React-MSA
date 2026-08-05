package com.springmsa.memberbff.kafka.consumer;

import com.springmsa.kafka.topic.MsaKafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true")
public class MemberDomainDeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(MemberDomainDeadLetterConsumer.class);

    @KafkaListener(
            topics = {
                    MsaKafkaTopics.COMMUNITY_POST_CREATED_V1_DLT,
                    MsaKafkaTopics.USER_REGISTERED_V1_DLT,
                    MsaKafkaTopics.WATCHLIST_ITEM_ADDED_V1_DLT
            },
            groupId = "spring-member-bff-domain-dlt-monitor"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.error(
                "Domain event moved to DLT. topic={}, partition={}, offset={}, key={}, payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value()
        );
    }
}

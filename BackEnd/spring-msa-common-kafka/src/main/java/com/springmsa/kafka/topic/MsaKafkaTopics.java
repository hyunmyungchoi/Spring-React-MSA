package com.springmsa.kafka.topic;

public final class MsaKafkaTopics {

    public static final String CHAT_MESSAGE_CREATED = "spring.chat.message.created";
    public static final String CHAT_MESSAGE_CREATED_DLT = CHAT_MESSAGE_CREATED + ".DLT";

    private MsaKafkaTopics() {
    }
}

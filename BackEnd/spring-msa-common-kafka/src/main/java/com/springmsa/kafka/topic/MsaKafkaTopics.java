package com.springmsa.kafka.topic;

public final class MsaKafkaTopics {

    public static final String CHAT_MESSAGE_CREATED = "spring.chat.message.created";
    public static final String CHAT_MESSAGE_CREATED_DLT = CHAT_MESSAGE_CREATED + ".DLT";
    public static final String COMMUNITY_POST_CREATED_V1 = "springmsa.community.post-created.v1";
    public static final String COMMUNITY_POST_CREATED_V1_DLT = COMMUNITY_POST_CREATED_V1 + ".DLT";
    public static final String USER_REGISTERED_V1 = "springmsa.user.registered.v1";
    public static final String USER_REGISTERED_V1_DLT = USER_REGISTERED_V1 + ".DLT";
    public static final String WATCHLIST_ITEM_ADDED_V1 = "springmsa.stock.watchlist-item-added.v1";
    public static final String WATCHLIST_ITEM_ADDED_V1_DLT = WATCHLIST_ITEM_ADDED_V1 + ".DLT";

    private MsaKafkaTopics() {
    }
}

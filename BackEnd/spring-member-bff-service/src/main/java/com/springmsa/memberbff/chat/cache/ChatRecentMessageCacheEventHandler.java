package com.springmsa.memberbff.chat.cache;

import com.springmsa.memberbff.chat.event.ChatMessageSavedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ChatRecentMessageCacheEventHandler {

    private final ChatRecentMessageCacheRepository chatRecentMessageCacheRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void appendMessage(ChatMessageSavedEvent event) {
        chatRecentMessageCacheRepository.appendMessage(event.message());
    }
}

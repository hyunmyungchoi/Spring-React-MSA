package com.springmsa.memberstockservice.watchlist.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.watchlist.error.StockWatchItemErrorCode;
import com.springmsa.memberstockservice.watchlist.repository.StockWatchItemRepository;
import com.springmsa.memberstockservice.outbox.OutboxEventWriter;
import com.springmsa.kafka.event.MsaEventEnvelope;
import com.springmsa.kafka.event.WatchlistItemAddedEvent;
import com.springmsa.kafka.topic.MsaKafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockWatchItemService {

    private final StockWatchItemRepository repository;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional(readOnly = true)
    public List<StockWatchItemResponse> findAll(String ownerSub) {
        return repository.findAllByOwnerSubOrderByCreatedAtDesc(ownerSub).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StockWatchItemResponse create(StockWatchItemRequest request, String owner) {
        try {
            StockWatchItem savedItem = repository.save(StockWatchItem.create(owner, request.symbol(), request.memo()));
            MsaEventEnvelope<WatchlistItemAddedEvent> event = MsaEventEnvelope.create(
                    "watchlist.item-added", 1, "spring-member-stock-service", savedItem.getCreatedAt(),
                    new WatchlistItemAddedEvent(
                            savedItem.getId(), savedItem.getOwnerSub(), savedItem.getSymbol(),
                            savedItem.getMemo(), savedItem.getCreatedAt()
                    )
            );
            outboxEventWriter.append(
                    "StockWatchItem", savedItem.getId().toString(), MsaKafkaTopics.WATCHLIST_ITEM_ADDED_V1,
                    savedItem.getOwnerSub(), event
            );
            return toResponse(savedItem);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(StockWatchItemErrorCode.WATCH_ITEM_DUPLICATE, exception);
        }
    }

    @Transactional
    public StockWatchItemResponse update(Long itemId, StockWatchItemRequest request, String ownerSub) {
        StockWatchItem item = repository.findByIdAndOwnerSub(itemId, ownerSub)
                .orElseThrow(() -> new ApiException(StockWatchItemErrorCode.WATCH_ITEM_NOT_FOUND));

        try {
            item.update(request.symbol(), request.memo());
            return toResponse(repository.save(item));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(StockWatchItemErrorCode.WATCH_ITEM_DUPLICATE, exception);
        }
    }

    @Transactional
    public void delete(Long itemId, String ownerSub) {
        StockWatchItem item = repository.findByIdAndOwnerSub(itemId, ownerSub)
                .orElseThrow(() -> new ApiException(StockWatchItemErrorCode.WATCH_ITEM_NOT_FOUND));

        repository.delete(item);
    }

    private StockWatchItemResponse toResponse(StockWatchItem item) {
        return new StockWatchItemResponse(
                item.getId(),
                item.getSymbol(),
                item.getMemo(),
                item.getOwnerSub(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

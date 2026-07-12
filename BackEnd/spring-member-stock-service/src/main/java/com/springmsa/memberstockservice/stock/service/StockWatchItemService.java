package com.springmsa.memberstockservice.stock.service;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.stock.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.stock.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.stock.error.StockWatchItemErrorCode;
import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import com.springmsa.memberstockservice.watchlist.repository.StockWatchItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StockWatchItemService {

    private final StockWatchItemRepository repository;

    public StockWatchItemService(StockWatchItemRepository repository) {
        this.repository = repository;
    }

    public List<StockWatchItemResponse> findAll(String ownerSub) {
        return repository.findAllByOwnerSubOrderByCreatedAtDesc(ownerSub).stream()
                .map(this::toResponse)
                .toList();
    }

    public StockWatchItemResponse create(StockWatchItemRequest request, String owner) {
        try {
            return toResponse(repository.save(StockWatchItem.create(owner, request.symbol(), request.memo())));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(StockWatchItemErrorCode.WATCH_ITEM_DUPLICATE, exception);
        }
    }

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

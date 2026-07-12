package com.springmsa.memberstockservice.watchlist;

import com.springmsa.common.web.error.ApiException;
import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.watchlist.repository.StockWatchItemRepository;
import com.springmsa.memberstockservice.watchlist.service.StockWatchItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockWatchItemServiceTest {

    private final StockWatchItemRepository repository = mock(StockWatchItemRepository.class);

    private StockWatchItemService service;

    @BeforeEach
    void setUp() {
        service = new StockWatchItemService(repository);
    }

    @Test
    void listReturnsOnlyTheAuthenticatedOwnersItems() {
        StockWatchItem item = watchItem(1L, "user-a", "005930", "Samsung");
        when(repository.findAllByOwnerSubOrderByCreatedAtDesc("user-a")).thenReturn(List.of(item));

        assertThat(service.findAll("user-a"))
                .extracting(StockWatchItemResponse::owner)
                .containsExactly("user-a");

        verify(repository).findAllByOwnerSubOrderByCreatedAtDesc("user-a");
    }

    @Test
    void createNormalizesSymbolBeforePersistence() {
        when(repository.save(any(StockWatchItem.class))).thenAnswer(invocation -> {
            StockWatchItem item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 7L);
            return item;
        });

        StockWatchItemResponse response = service.create(new StockWatchItemRequest(" aapl ", "Apple"), "user-a");

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.owner()).isEqualTo("user-a");
    }

    @Test
    void duplicateOwnerAndSymbolReturnsConflict() {
        when(repository.save(any(StockWatchItem.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(new StockWatchItemRequest("005930", "Samsung"), "user-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("WATCH_ITEM_DUPLICATE");
                    assertThat(apiException.status().value()).isEqualTo(409);
                });
    }

    @Test
    void updateRequiresAuthenticatedOwner() {
        when(repository.findByIdAndOwnerSub(99L, "user-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, new StockWatchItemRequest("MSFT", "Microsoft"), "user-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("WATCH_ITEM_NOT_FOUND");
                    assertThat(apiException.status().value()).isEqualTo(404);
                });

        verify(repository, never()).save(any());
    }

    @Test
    void updateOwnItemNormalizesSymbol() {
        StockWatchItem item = watchItem(5L, "user-a", "005930", "Samsung");
        when(repository.findByIdAndOwnerSub(5L, "user-a")).thenReturn(Optional.of(item));
        when(repository.save(item)).thenReturn(item);

        StockWatchItemResponse response = service.update(
                5L,
                new StockWatchItemRequest(" msft ", "Microsoft"),
                "user-a"
        );

        assertThat(response.symbol()).isEqualTo("MSFT");
        assertThat(response.memo()).isEqualTo("Microsoft");
        verify(repository).findByIdAndOwnerSub(5L, "user-a");
    }

    @Test
    void deleteRequiresAuthenticatedOwner() {
        when(repository.findByIdAndOwnerSub(99L, "user-a")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, "user-a"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.code()).isEqualTo("WATCH_ITEM_NOT_FOUND");
                    assertThat(apiException.status().value()).isEqualTo(404);
                });

        verify(repository, never()).delete(any());
    }

    private static StockWatchItem watchItem(Long id, String ownerSub, String symbol, String memo) {
        StockWatchItem item = StockWatchItem.create(ownerSub, symbol, memo);
        Instant now = Instant.parse("2026-07-12T10:15:30Z");
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "createdAt", now);
        ReflectionTestUtils.setField(item, "updatedAt", now);
        return item;
    }
}

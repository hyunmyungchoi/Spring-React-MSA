package com.springmsa.memberstockservice.watchlist.controller;

import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemRequest;
import com.springmsa.memberstockservice.watchlist.dto.StockWatchItemResponse;
import com.springmsa.memberstockservice.watchlist.service.StockWatchItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockWatchItemControllerTest {

    private final StockWatchItemService service = mock(StockWatchItemService.class);
    private final Authentication authentication = mock(Authentication.class);

    private StockWatchItemController controller;

    @BeforeEach
    void setUp() {
        controller = new StockWatchItemController(service);
        when(authentication.getName()).thenReturn("user-a");
    }

    @Test
    void findAllUsesAuthenticatedSubject() {
        StockWatchItemResponse item = response(1L, "005930", "user-a");
        when(service.findAll("user-a")).thenReturn(List.of(item));

        assertThat(controller.findAll(authentication)).containsExactly(item);

        verify(service).findAll("user-a");
    }

    @Test
    void updateUsesAuthenticatedSubject() {
        StockWatchItemRequest request = new StockWatchItemRequest("AAPL", "Apple");
        StockWatchItemResponse item = response(3L, "AAPL", "user-a");
        when(service.update(3L, request, "user-a")).thenReturn(item);

        assertThat(controller.update(3L, request, authentication)).isEqualTo(item);

        verify(service).update(3L, request, "user-a");
    }

    @Test
    void deleteUsesAuthenticatedSubject() {
        controller.delete(3L, authentication);

        verify(service).delete(3L, "user-a");
    }

    private static StockWatchItemResponse response(Long id, String symbol, String owner) {
        Instant now = Instant.parse("2026-07-12T10:15:30Z");
        return new StockWatchItemResponse(id, symbol, "memo", owner, now, now);
    }
}

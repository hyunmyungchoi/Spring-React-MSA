package com.springmsa.memberstockservice.watchlist;

import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import com.springmsa.memberstockservice.watchlist.repository.StockWatchItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class StockWatchItemRepositoryTest {

    @Autowired
    StockWatchItemRepository repository;

    @Test
    void returnsOnlyTheOwnersItems() {
        repository.save(StockWatchItem.create("user-1", "005930", "삼성전자"));
        repository.save(StockWatchItem.create("user-2", "AAPL", "애플"));

        List<StockWatchItem> result = repository.findAllByOwnerSubOrderByCreatedAtDesc("user-1");

        assertThat(result).extracting(StockWatchItem::getSymbol).containsExactly("005930");
    }
}

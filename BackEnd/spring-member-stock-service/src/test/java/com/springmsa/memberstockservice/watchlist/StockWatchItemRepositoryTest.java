package com.springmsa.memberstockservice.watchlist;

import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import com.springmsa.memberstockservice.watchlist.repository.StockWatchItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTestContextBootstrapper;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.util.ReflectionTestUtils;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@BootstrapWith(SpringBootTestContextBootstrapper.class)
@ContextConfiguration(
        classes = StockWatchItemRepositoryTest.TestJpaConfiguration.class,
        initializers = ConfigDataApplicationContextInitializer.class
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-watch-item-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Transactional
class StockWatchItemRepositoryTest {

    @Autowired
    StockWatchItemRepository repository;

    @Test
    void returnsOnlyTheOwnersItemsInDescendingCreatedAtOrder() {
        StockWatchItem oldestOwnerItem = createItem("user-1", "005930", "Samsung", "2026-01-01T00:00:00Z");
        StockWatchItem otherOwnerItem = createItem("user-2", "AAPL", "Apple", "2026-01-02T00:00:00Z");
        StockWatchItem newestOwnerItem = createItem("user-1", "MSFT", "Microsoft", "2026-01-03T00:00:00Z");

        repository.save(oldestOwnerItem);
        repository.save(otherOwnerItem);
        repository.save(newestOwnerItem);

        List<StockWatchItem> result = repository.findAllByOwnerSubOrderByCreatedAtDesc("user-1");

        assertThat(result)
                .extracting(StockWatchItem::getSymbol)
                .containsExactly("MSFT", "005930");
        assertThat(result)
                .extracting(StockWatchItem::getOwnerSub)
                .containsOnly("user-1");
    }

    private static StockWatchItem createItem(String ownerSub, String symbol, String memo, String createdAt) {
        StockWatchItem item = StockWatchItem.create(ownerSub, symbol, memo);
        Instant timestamp = Instant.parse(createdAt);
        ReflectionTestUtils.setField(item, "createdAt", timestamp);
        ReflectionTestUtils.setField(item, "updatedAt", timestamp);
        return item;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = StockWatchItem.class)
    @EnableJpaRepositories(basePackageClasses = StockWatchItemRepository.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    static class TestJpaConfiguration {
    }
}

package com.springmsa.memberstockservice.watchlist.repository;

import com.springmsa.memberstockservice.watchlist.domain.StockWatchItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockWatchItemRepository extends JpaRepository<StockWatchItem, Long> {

    List<StockWatchItem> findAllByOwnerSubOrderByCreatedAtDesc(String ownerSub);

    Optional<StockWatchItem> findByIdAndOwnerSub(Long id, String ownerSub);
}

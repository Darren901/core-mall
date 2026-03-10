package com.coremall.inventory.jpa;

import com.coremall.inventory.jpa.entity.Inventory;
import com.coremall.inventory.jpa.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("InventoryRepository - 庫存資料存取")
class InventoryRepositoryTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    @DisplayName("以 productName 查詢庫存，找到時返回 Optional 含庫存資料")
    void shouldFindInventoryByProductName() {
        Inventory inventory = new Inventory("iPhone 15", 10);
        inventoryRepository.save(inventory);

        Optional<Inventory> found = inventoryRepository.findById("iPhone 15");

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("查詢不存在的 productName，返回 Optional.empty()")
    void shouldReturnEmptyWhenProductNotFound() {
        Optional<Inventory> found = inventoryRepository.findById("NonExistentProduct");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("更新庫存數量後，查詢返回更新後的值")
    void shouldPersistUpdatedQuantity() {
        Inventory inventory = new Inventory("MacBook Pro", 5);
        inventoryRepository.save(inventory);

        inventory.deduct(2);
        inventoryRepository.save(inventory);

        Optional<Inventory> found = inventoryRepository.findById("MacBook Pro");
        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(3);
    }
}

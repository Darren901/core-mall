package com.coremall.inventory.config;

import com.coremall.inventory.jpa.entity.Inventory;
import com.coremall.inventory.jpa.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final InventoryRepository inventoryRepository;

    public DataInitializer(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void run(String... args) {
        inventoryRepository.save(new Inventory("iPhone 15", 10));
        inventoryRepository.save(new Inventory("MacBook Pro", 5));
        inventoryRepository.save(new Inventory("AirPods", 20));
        log.info("[DataInitializer] 種子庫存資料已寫入：iPhone 15 × 10, MacBook Pro × 5, AirPods × 20");
    }
}

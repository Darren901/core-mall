package com.coremall.inventory.jpa.repository;

import com.coremall.inventory.jpa.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryRepository extends JpaRepository<Inventory, String> {
}

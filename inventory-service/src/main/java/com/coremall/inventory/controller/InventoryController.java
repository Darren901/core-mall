package com.coremall.inventory.controller;

import com.coremall.inventory.dto.InventoryResponse;
import com.coremall.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productName}")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable String productName) {
        return inventoryService.getStock(productName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}

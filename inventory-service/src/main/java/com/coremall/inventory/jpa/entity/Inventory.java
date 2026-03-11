package com.coremall.inventory.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_name")
    private String productName;

    @Column(nullable = false)
    private int quantity;

    protected Inventory() {}

    public Inventory(String productName, int quantity) {
        this.productName = productName;
        this.quantity = quantity;
    }

    public void deduct(int amount) {
        this.quantity -= amount;
    }

    public void restock(int amount) {
        this.quantity += amount;
    }

    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
}

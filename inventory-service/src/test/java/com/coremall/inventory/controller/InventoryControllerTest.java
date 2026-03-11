package com.coremall.inventory.controller;

import com.coremall.inventory.dto.InventoryResponse;
import com.coremall.inventory.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@DisplayName("GET /api/inventory/{productName}")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    @DisplayName("商品存在時回傳 200 與庫存數量")
    void shouldReturn200WithQuantityWhenProductExists() throws Exception {
        given(inventoryService.getStock("iPhone 15"))
                .willReturn(Optional.of(new InventoryResponse("iPhone 15", 10)));

        mockMvc.perform(get("/api/inventory/{productName}", "iPhone 15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("iPhone 15"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    @DisplayName("商品不存在時回傳 404")
    void shouldReturn404WhenProductNotFound() throws Exception {
        given(inventoryService.getStock("不存在商品"))
                .willReturn(Optional.empty());

        mockMvc.perform(get("/api/inventory/{productName}", "不存在商品"))
                .andExpect(status().isNotFound());
    }
}

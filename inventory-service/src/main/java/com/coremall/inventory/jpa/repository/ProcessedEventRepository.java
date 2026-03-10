package com.coremall.inventory.jpa.repository;

import com.coremall.inventory.jpa.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}

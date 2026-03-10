package com.coremall.inventory.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(String messageId) {
        ProcessedEvent e = new ProcessedEvent();
        e.messageId = messageId;
        e.processedAt = LocalDateTime.now();
        return e;
    }

    public String getMessageId() { return messageId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}

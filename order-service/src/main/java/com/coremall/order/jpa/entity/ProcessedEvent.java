package com.coremall.order.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(String eventId) {
        ProcessedEvent e = new ProcessedEvent();
        e.eventId = eventId;
        e.processedAt = LocalDateTime.now();
        return e;
    }

    public String getEventId() { return eventId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}

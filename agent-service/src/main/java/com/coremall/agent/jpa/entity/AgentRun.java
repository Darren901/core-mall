package com.coremall.agent.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userMessage;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String finalReply;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected AgentRun() {}

    public static AgentRun create(String userMessage) {
        AgentRun run = new AgentRun();
        run.id = UUID.randomUUID(); // 預設 ID，Hibernate 會直接使用（不覆蓋非 null 的 UUID）
        run.userMessage = userMessage;
        run.status = "RUNNING";
        run.createdAt = OffsetDateTime.now();
        run.updatedAt = OffsetDateTime.now();
        return run;
    }

    public void complete(String finalReply) {
        this.finalReply = finalReply;
        this.status = "COMPLETED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(String error) {
        this.finalReply = error;
        this.status = "FAILED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public String getUserMessage() { return userMessage; }
    public String getStatus() { return status; }
    public String getFinalReply() { return finalReply; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

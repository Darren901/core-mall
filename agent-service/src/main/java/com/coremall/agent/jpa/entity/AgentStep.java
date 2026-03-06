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
@Table(name = "agent_steps")
public class AgentStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID runId;

    @Column(nullable = false, length = 100)
    private String toolName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    protected AgentStep() {}

    public static AgentStep start(UUID runId, String toolName) {
        AgentStep step = new AgentStep();
        step.runId = runId;
        step.toolName = toolName;
        step.status = "STARTED";
        step.createdAt = OffsetDateTime.now();
        step.updatedAt = OffsetDateTime.now();
        return step;
    }

    public void succeed(String payload) {
        this.payload = payload;
        this.status = "SUCCEEDED";
        this.updatedAt = OffsetDateTime.now();
    }

    public void fail(String error) {
        this.error = error;
        this.status = "FAILED";
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getRunId() { return runId; }
    public String getToolName() { return toolName; }
    public String getStatus() { return status; }
    public String getPayload() { return payload; }
    public String getError() { return error; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

package com.coremall.agent.jpa.repository;

import com.coremall.agent.jpa.entity.AgentStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentStepRepository extends JpaRepository<AgentStep, UUID> {

    List<AgentStep> findByRunIdOrderByCreatedAtAsc(UUID runId);
}

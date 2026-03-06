package com.coremall.agent.jpa.repository;

import com.coremall.agent.jpa.entity.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
}

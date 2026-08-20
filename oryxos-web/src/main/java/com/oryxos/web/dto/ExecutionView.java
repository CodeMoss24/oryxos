package com.oryxos.web.dto;

import com.oryxos.core.agent.AgentExecution;
import java.time.Instant;

/** Agent 执行历史视图(agent_executions 表)。status = RUNNING / SUCCESS / FAILED。 */
public record ExecutionView(
    long id,
    String agentName,
    String source,
    String sessionId,
    Instant startedAt,
    Instant endedAt,
    String status,
    Long durationMs,
    String errorMessage) {

  public static ExecutionView from(AgentExecution e) {
    return new ExecutionView(
        e.id(),
        e.agentName(),
        e.source(),
        e.sessionId(),
        e.startedAt(),
        e.endedAt(),
        e.status(),
        e.durationMs(),
        e.errorMessage());
  }
}

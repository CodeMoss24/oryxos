package com.oryxos.provider;

import com.oryxos.core.react.ToolAuditPort;
import com.oryxos.storage.entity.ToolInvocationEntity;
import com.oryxos.storage.repository.ToolInvocationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolAuditAdapter implements ToolAuditPort {

  private static final Logger log = LoggerFactory.getLogger(ToolAuditAdapter.class);

  private final ToolInvocationRepository toolInvocationRepository;

  public ToolAuditAdapter(ToolInvocationRepository toolInvocationRepository) {
    this.toolInvocationRepository = toolInvocationRepository;
  }

  @Override
  public void record(
      String sessionId,
      String toolName,
      String inputJson,
      String resultJson,
      boolean success,
      String errorMessage,
      long durationMs) {
    try {
      ToolInvocationEntity entity = new ToolInvocationEntity();
      entity.setSessionId(sessionId);
      entity.setToolName(toolName);
      entity.setInputJson(inputJson);
      entity.setResultJson(resultJson);
      entity.setSuccess(success);
      entity.setErrorMessage(errorMessage);
      entity.setDurationMs(durationMs);
      entity.setCreatedAt(Instant.now());
      toolInvocationRepository.save(entity);
    } catch (Exception e) {
      log.error(
          "Failed to write tool_invocations audit record for session {} tool {}",
          sessionId,
          toolName,
          e);
    }
  }
}

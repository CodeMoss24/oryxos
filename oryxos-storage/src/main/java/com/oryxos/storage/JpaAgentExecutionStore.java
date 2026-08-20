package com.oryxos.storage;

import com.oryxos.core.agent.AgentExecution;
import com.oryxos.core.agent.AgentExecutionStore;
import com.oryxos.storage.entity.AgentExecutionEntity;
import com.oryxos.storage.repository.AgentExecutionRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AgentExecutionStore 的 JPA 实现(依赖倒置:契约在 core,实现在 storage)。
 *
 * <p>时间字段存储为 String(ISO-8601 文本),与既有五表同口径。 start 落一条 ended_at 为空的"运行中"记录并返回自增主键;finish
 * 按主键回填结束信息;listByAgent 按 id 倒序(等价于开始时间倒序)取最近 limit 条。
 */
@Component
public class JpaAgentExecutionStore implements AgentExecutionStore {

  private final AgentExecutionRepository repository;

  public JpaAgentExecutionStore(AgentExecutionRepository repository) {
    this.repository = repository;
  }

  @Override
  public long start(String agentName, String source, Instant startedAt) {
    AgentExecutionEntity entity = new AgentExecutionEntity();
    entity.setAgentName(agentName);
    entity.setSource(source);
    entity.setStartedAt(startedAt != null ? startedAt.toString() : null);
    return repository.save(entity).getId();
  }

  @Override
  public void finish(
      long id, String sessionId, boolean success, String errorMessage, Instant endedAt) {
    repository
        .findById(id)
        .ifPresent(
            entity -> {
              entity.setSessionId(sessionId);
              entity.setSuccess(success);
              entity.setErrorMessage(errorMessage);
              entity.setEndedAt(endedAt != null ? endedAt.toString() : null);
              if (entity.getStartedAt() != null && endedAt != null) {
                try {
                  entity.setDurationMs(
                      java.time.Duration.between(Instant.parse(entity.getStartedAt()), endedAt)
                          .toMillis());
                } catch (Exception ignored) {
                  entity.setDurationMs(null);
                }
              }
              repository.save(entity);
            });
  }

  @Override
  public List<AgentExecution> listByAgent(String agentName, int limit) {
    return repository.findTop50ByAgentNameOrderByIdDesc(agentName).stream()
        .map(JpaAgentExecutionStore::toView)
        .toList();
  }

  private static AgentExecution toView(AgentExecutionEntity e) {
    return new AgentExecution(
        e.getId() != null ? e.getId() : 0L,
        e.getAgentName(),
        e.getSource(),
        e.getSessionId(),
        parseInstant(e.getStartedAt()),
        parseInstant(e.getEndedAt()),
        e.getSuccess(),
        e.getDurationMs(),
        e.getErrorMessage());
  }

  private static Instant parseInstant(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(s);
    } catch (Exception ex) {
      return null;
    }
  }
}

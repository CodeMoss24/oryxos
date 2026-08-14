package com.oryxos.core;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 三种触发源共用的统一入口,也是一次处理的编排者。
 *
 * <p>process(Session, String) 内部依次做:
 *
 * <ol>
 *   <li>把当前 Profile 放进 ProfileContext(ThreadLocal,虚拟线程下每个请求天然独立)
 *   <li>调 ReActLoop.run 跑完循环
 *   <li>通过 SessionPersistencePort 持久化 Session
 *   <li>finally 里清掉 ProfileContext
 * </ol>
 *
 * <p>CLI / Web Service / AgentScheduler 三个入口都调本方法,ReActLoop 不感知消息从哪个入口来。
 */
@Service
public class AgentService {

  private static final Logger log = LoggerFactory.getLogger(AgentService.class);

  private final ProfileRegistry profileRegistry;
  private final AgentLoader agentLoader;
  private final ReActLoop reActLoop;
  private final SessionPersistencePort sessionPersistencePort;

  public AgentService(
      ProfileRegistry profileRegistry,
      AgentLoader agentLoader,
      ReActLoop reActLoop,
      SessionPersistencePort sessionPersistencePort) {
    this.profileRegistry = profileRegistry;
    this.agentLoader = agentLoader;
    this.reActLoop = reActLoop;
    this.sessionPersistencePort = sessionPersistencePort;
  }

  public String process(Session session, String userMessage) {
    Profile profile =
        profileRegistry
            .find(session.getProfileName())
            .orElseThrow(
                () -> new IllegalStateException("Profile not found: " + session.getProfileName()));
    try {
      ProfileContext.set(profile);
      String agentMdBody = loadAgentMdBody(profile.getName());
      String reply = reActLoop.run(session, userMessage, profile, agentMdBody);
      sessionPersistencePort.save(session);
      return reply;
    } finally {
      ProfileContext.clear();
    }
  }

  private String loadAgentMdBody(String profileName) {
    try {
      java.nio.file.Path agentMd =
          com.oryxos.core.runtime.OryxOsRuntime.resolve("agents", profileName, "AGENT.md");
      if (java.nio.file.Files.exists(agentMd)) {
        AgentLoader.ParsedAgentMd parsed = agentLoader.parseAgentMd(agentMd);
        return parsed.body();
      }
    } catch (Exception e) {
      log.warn("Failed to load AGENT.md body for {}: {}", profileName, e.getMessage());
    }
    return "";
  }
}

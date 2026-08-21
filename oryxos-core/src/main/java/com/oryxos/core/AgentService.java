package com.oryxos.core;

import com.oryxos.core.profile.AgentLoader;
import com.oryxos.core.profile.Profile;
import com.oryxos.core.profile.ProfileContext;
import com.oryxos.core.profile.ProfileRegistry;
import com.oryxos.core.react.ReActLoop;
import com.oryxos.core.session.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

  /** 输出文件按天分块:文件名 yyyy-MM-dd.md,块头精确到秒。 */
  private static final DateTimeFormatter OUTPUT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

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
      appendOutput(profile.getName(), session, userMessage, reply);
      return reply;
    } finally {
      ProfileContext.clear();
    }
  }

  /**
   * 执行产物落盘:每次处理完成后追加到 {@code output/<agent>/<yyyy-MM-dd>.md}——管理台「输出」tab 从这里读。 三种触发源
   * (CLI/Web/定时)共用本方法,来源按会话 channel 区分;失败只记日志,不影响主流程。
   */
  private void appendOutput(String profileName, Session session, String userMessage, String reply) {
    try {
      Path outDir = com.oryxos.core.runtime.OryxOsRuntime.resolve("output", profileName);
      Files.createDirectories(outDir);
      Path file = outDir.resolve(LocalDate.now() + ".md");
      String source =
          switch (session.getChannel() == null
              ? ""
              : session.getChannel().toLowerCase(Locale.ROOT)) {
            case "scheduler" -> "定时";
            case "invoke" -> "手动调用";
            case "admin" -> "管理台";
            case "cli" -> "CLI";
            case "web" -> "Web";
            default -> session.getChannel() == null ? "未知" : session.getChannel();
          };
      String block =
          "\n## "
              + LocalDateTime.now().format(OUTPUT_TIME)
              + " · "
              + source
              + "\n\n**触发**: "
              + userMessage
              + "\n\n**回复**: "
              + reply
              + "\n";
      Files.writeString(
          file,
          block,
          StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (Exception e) {
      log.warn("Failed to write output for {}: {}", profileName, e.getMessage());
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

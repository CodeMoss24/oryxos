package com.oryxos.channel.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Session;
import com.oryxos.core.session.SessionManager;
import java.util.Scanner;
import org.springframework.stereotype.Component;

/**
 * CLI Channel 实现。oryxos chat 命令的执行体, 读 stdin 写 stdout 实现交互式对话,维护当前 Session,每次输入调
 * AgentService.process。
 *
 * <p>Session 标识只提供三元组(channel=cli + 本机用户 + profile),id 拼接由 SessionManager 完成; getOrCreate
 * 幂等——同一三元组历次进入同一个 Session,历史自动带回。
 *
 * <p>支持 /quit 退出。
 */
@Component
public class CliChannel {

  private final AgentService agentService;
  private final SessionManager sessionManager;

  public CliChannel(AgentService agentService, SessionManager sessionManager) {
    this.agentService = agentService;
    this.sessionManager = sessionManager;
  }

  public void start(String profileName, String singleMessage) {
    Session session = sessionManager.getOrCreate("cli", currentUser(), profileName);
    Scanner scanner = new Scanner(System.in);

    if (singleMessage != null && !singleMessage.isBlank()) {
      String reply = agentService.process(session, singleMessage);
      System.out.println(reply);
      return;
    }

    System.out.println("OryxOS CLI — profile: " + profileName + " (input /quit to exit)");
    while (true) {
      System.out.print("> ");
      if (!scanner.hasNextLine()) break;
      String input = scanner.nextLine().trim();
      if ("/quit".equalsIgnoreCase(input)) break;
      if (input.isEmpty()) continue;
      String reply = agentService.process(session, input);
      System.out.println(reply);
    }
  }

  private String currentUser() {
    String user = System.getProperty("user.name");
    return (user == null || user.isBlank()) ? "console" : user;
  }
}

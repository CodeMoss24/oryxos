package com.oryxos.channel.cli;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Session;
import java.util.Scanner;
import org.springframework.stereotype.Component;

/**
 * CLI Channel 实现。oryxos chat 命令的执行体, 读 stdin 写 stdout 实现交互式对话,维护当前 Session,每次输入调
 * AgentService.process。
 *
 * <p>支持 /quit 退出。
 */
@Component
public class CliChannel {

  private final AgentService agentService;

  public CliChannel(AgentService agentService) {
    this.agentService = agentService;
  }

  public void start(String profileName, String singleMessage) {
    String sessionId = "cli+console+" + profileName;
    Session session = new Session(sessionId, profileName, "cli", "console");
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
}

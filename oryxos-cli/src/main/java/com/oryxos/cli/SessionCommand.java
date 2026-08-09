package com.oryxos.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import picocli.CommandLine.Command;

/** oryxos session list — 列出会话历史。轻命令:JDBC 直连本地 SQLite,不起 Spring。 */
@Command(name = "session", description = "列出会话历史", mixinStandardHelpOptions = true)
public class SessionCommand implements Runnable {

  @Override
  public void run() {
    Path db = Path.of(".oryxos", "oryxos.db");
    if (!Files.isRegularFile(db)) {
      System.out.println("No sessions yet (.oryxos/oryxos.db not found)");
      return;
    }
    String url = "jdbc:sqlite:" + db;
    try (Connection conn = DriverManager.getConnection(url);
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(
            "SELECT session_id, profile_name, channel, user_id, last_active_at FROM sessions "
                + "ORDER BY last_active_at DESC")) {
      boolean any = false;
      while (rs.next()) {
        any = true;
        System.out.printf(
            "%s  %s  channel=%s user=%s  last_active=%s%n",
            rs.getString("session_id"),
            rs.getString("profile_name"),
            rs.getString("channel"),
            rs.getString("user_id"),
            rs.getString("last_active_at"));
      }
      if (!any) {
        System.out.println("No sessions yet");
      }
    } catch (Exception e) {
      System.out.println("Unable to read sessions: " + e.getMessage());
    }
  }
}

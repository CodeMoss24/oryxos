package com.oryxos.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.oryxos.core.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 第 29 节 harness:渐进式披露。 Agent 正文进 system prompt(常驻);目录里的子指令/参考/脚本不预载——靠底座 read_file/shell 按需取。
 * 本测试钉死 ContextLoader.loadSystemPrompt 只注正文,不把 skills/REFERENCE.md/scripts 内容塞进 prompt。
 */
@DisplayName("ProgressiveDisclosure — 正文进 prompt、资源不预载")
class ProgressiveDisclosureTest {

  @TempDir Path workspace;

  @Test
  @DisplayName("system prompt 含 AGENT.md 正文,不含子指令/参考/脚本内容")
  void bodyInPrompt_resourcesNotPreloaded() throws Exception {
    // 构造一个 Agent 目录:AGENT.md(正文) + skills/report-format.md + REFERENCE.md + scripts/reconcile.py
    Path agentsDir = workspace.resolve(".oryxos/agents/recon");
    Files.createDirectories(agentsDir.resolve("skills"));
    Files.createDirectories(agentsDir.resolve("scripts"));
    Files.writeString(
        agentsDir.resolve("AGENT.md"),
        """
                ---
                provider:
                  name: deepseek
                  model: deepseek-chat
                ---
                你是每日订单对账助手。被触发时按顺序做。
                """);
    Files.writeString(agentsDir.resolve("skills/report-format.md"), "# 对账差异报告规范\nP0/P1/P2 分级...");
    Files.writeString(agentsDir.resolve("REFERENCE.md"), "# 对账参考资料\n字段对照...");
    Files.writeString(agentsDir.resolve("scripts/reconcile.py"), "import csv, json\n# script code");

    // ContextLoader 用 workspace 根解析 bootstrap 相对路径;这里只验正文注入,不给 bootstrap
    ContextLoader loader = new ContextLoader(workspace);
    Profile profile = new Profile();
    profile.setName("recon");
    // 正文(去 frontmatter 后的部分)
    String body = "你是每日订单对账助手。被触发时按顺序做。";

    String prompt = loader.loadSystemPrompt(profile, body);

    // ① 正文进 prompt
    assertThat(prompt).contains("你是每日订单对账助手");
    // ② 子指令不预载
    assertThat(prompt).doesNotContain("对账差异报告规范");
    assertThat(prompt).doesNotContain("P0/P1/P2 分级");
    // ③ 参考不预载
    assertThat(prompt).doesNotContain("对账参考资料");
    assertThat(prompt).doesNotContain("字段对照");
    // ④ 脚本代码不预载
    assertThat(prompt).doesNotContain("import csv");
    assertThat(prompt).doesNotContain("script code");
  }

  @Test
  @DisplayName("正文为空时 prompt 仍含当前时间(不抛异常)")
  void emptyBodyStillWorks() {
    ContextLoader loader = new ContextLoader(workspace);
    Profile profile = new Profile();
    profile.setName("bare");

    String prompt = loader.loadSystemPrompt(profile, "");

    assertThat(prompt).contains("当前时间");
  }
}

package com.oryxos.tool.interaction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 终端交互实现:stdout 打印问题,stdin 读一行作为回答。
 *
 * <p>仅在交互式 CLI 环境装配(oryxos.cli.interactive=true)。读输入失败抛异常上抛——不吞, 由上层映射成 ToolResult.failure
 * 让模型知道交互失败。
 */
public class ConsoleUserInteraction implements UserInteraction {

  private final BufferedReader reader =
      new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

  @Override
  public String ask(String question) {
    System.out.print(question);
    try {
      return reader.readLine();
    } catch (IOException e) {
      throw new RuntimeException("读取用户输入失败: " + e.getMessage(), e);
    }
  }
}

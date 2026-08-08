package com.oryxos.tool;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import java.util.Map;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/** 启动时把 Spring 容器里所有 OryxTool Bean(内置 Tool 和方式三的 @Tool Plugin Tool) 注册到 ToolRegistry。 */
@Component
public class ToolAutoRegistrar implements ApplicationListener<ContextRefreshedEvent> {

  private final ToolRegistry toolRegistry;

  public ToolAutoRegistrar(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    Map<String, OryxTool> beans = event.getApplicationContext().getBeansOfType(OryxTool.class);
    beans.values().forEach(toolRegistry::register);
  }
}

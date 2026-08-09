package com.oryxos.tool;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.tool.adapter.AnnotatedToolAdapter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 启动时把 Spring 容器里的工具统一注册到 ToolRegistry,两个来源:
 *
 * <ol>
 *   <li>直接实现 OryxTool 的 Bean(如 19 节的 NotifyTools、外部直接提供的 OryxTool Bean);
 *   <li>FunctionCallback Bean(内置工具与方式三 Plugin Tool 的统一管道载体,见 AnnotatedToolAdapter)——先注册 OryxTool
 *       Bean 再注册 FunctionCallback 包装,同名时后者生效(NotifyTools 与内置工具无重名,顺序仅为确定性)。
 * </ol>
 */
@Component
public class ToolAutoRegistrar implements ApplicationListener<ContextRefreshedEvent> {

  private static final Logger log = LoggerFactory.getLogger(ToolAutoRegistrar.class);

  private final ToolRegistry toolRegistry;

  public ToolAutoRegistrar(ToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    Map<String, OryxTool> toolBeans = event.getApplicationContext().getBeansOfType(OryxTool.class);
    toolBeans.values().forEach(toolRegistry::register);

    Map<String, FunctionCallback> callbackBeans =
        event.getApplicationContext().getBeansOfType(FunctionCallback.class);
    callbackBeans
        .values()
        .forEach(
            fc -> {
              AnnotatedToolAdapter adapter = new AnnotatedToolAdapter(fc);
              toolRegistry.register(adapter);
              log.debug("Registered FunctionCallback tool: {}", adapter.getName());
            });
  }
}

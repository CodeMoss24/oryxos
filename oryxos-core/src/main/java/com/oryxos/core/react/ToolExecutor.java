package com.oryxos.core.react;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolRegistry;
import com.oryxos.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 执行 LLM 返回的 Tool 调用请求。
 * 从 ToolRegistry 找到对应 Tool,做 Sandbox 检查,执行 Tool,把结果包装成 ToolResult 返回给 ReAct 循环,
 * 并写入 tool_invocations 表。
 *
 * <p>Sandbox 检查由具体的 OryxTool 实现内部触发(如 FileTools/ShellTools/HttpTools 在 execute 开头调 sandbox.enforce)。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;

    public ToolExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public String execute(ToolCall call, Profile profile) {
        Optional<OryxTool> tool = toolRegistry.find(call.name());
        if (tool.isEmpty()) {
            log.warn("Tool not found: {}", call.name());
            return "Tool '" + call.name() + "' not found";
        }
        try {
            ToolResult result = tool.get().execute(call.argumentsJson());
            // TODO: 写入 tool_invocations 表(audit day one)
            if (result.success()) {
                return result.content();
            } else {
                return "Tool failed: " + result.errorMessage();
            }
        } catch (Exception e) {
            log.error("Tool execution error: {}", call.name(), e);
            return "Tool error: " + e.getMessage();
        }
    }
}

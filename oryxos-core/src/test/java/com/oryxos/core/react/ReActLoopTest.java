package com.oryxos.core.react;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.profile.Profile;
import com.oryxos.core.session.Message;
import com.oryxos.core.session.Session;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReActLoopTest {

  @Mock private PromptBuilder promptBuilder;
  @Mock private ProviderPort providerPort;
  @Mock private ToolExecutor toolExecutor;
  @Mock private com.oryxos.core.memory.MemoryService memoryService;
  @Mock private com.oryxos.core.tool.ToolRegistry toolRegistry;

  private ReActLoop loop;
  private Session session;
  private Profile profile;
  private static final String AGENT_MD_BODY = "你是一个助手";

  @BeforeEach
  void setUp() {
    loop = new ReActLoop(promptBuilder, providerPort, toolExecutor, memoryService, toolRegistry);
    session = new Session("s-1", "test-profile", "cli", "user1");
    profile = new Profile();
    profile.setName("test-profile");
    profile.getSettings().setMaxIterations(10);
  }

  private static LlmResponse textResponse(String content) {
    return new LlmResponse(content, List.of(), Usage.EMPTY);
  }

  private static LlmResponse toolCallResponse(String toolName, String args) {
    return new LlmResponse("", List.of(new ToolCall(toolName, args)), Usage.EMPTY);
  }

  @Test
  @DisplayName("无工具调用一轮收尾")
  void returnsResponseWhenNoToolCalls() {
    when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system prompt");
    when(promptBuilder.buildMemoryBlock(any(), any())).thenReturn("");
    when(promptBuilder.buildToolListBlock(any())).thenReturn("");
    when(promptBuilder.truncateHistory(any(), anyInt())).thenReturn(List.of());
    when(promptBuilder.assembleMessages(any(), any(), any(), any()))
        .thenReturn(List.of(Message.system("system prompt")));
    when(providerPort.chat(any(), any(), any())).thenReturn(textResponse("你好！"));

    String reply = loop.run(session, "你好", profile, AGENT_MD_BODY);

    assertEquals("你好！", reply);
    verify(providerPort, times(1)).chat(any(), any(), any());
    assertEquals(2, session.getMessages().size()); // user + assistant
  }

  @Test
  @DisplayName("有工具调用则执行并回填进下一轮")
  void executesToolAndContinuesLoop() {
    when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system prompt");
    when(promptBuilder.buildMemoryBlock(any(), any())).thenReturn("");
    when(promptBuilder.buildToolListBlock(any())).thenReturn("");
    when(promptBuilder.truncateHistory(any(), anyInt())).thenReturn(session.getMessages());
    when(promptBuilder.assembleMessages(any(), any(), any(), any()))
        .thenReturn(List.of(Message.system("system prompt")));
    when(providerPort.chat(any(), any(), any()))
        .thenReturn(toolCallResponse("http_get", "{\"url\":\"http://weather\"}"))
        .thenReturn(textResponse("今天适合穿薄外套"));
    when(toolExecutor.execute(any(), any(), any())).thenReturn("晴天 20°C");

    String reply = loop.run(session, "查天气", profile, AGENT_MD_BODY);

    assertEquals("今天适合穿薄外套", reply);
    verify(providerPort, times(2)).chat(any(), any(), any());
    verify(toolExecutor, times(1)).execute(any(), any(), any());
    assertTrue(session.getMessages().size() >= 4); // user + assistant + tool + assistant
  }

  @Test
  @DisplayName("模型一直要调工具_转满最大轮数强制停")
  void stopsAtMaxIterationsWhenModelNeverConverges() {
    when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system prompt");
    when(promptBuilder.buildMemoryBlock(any(), any())).thenReturn("");
    when(promptBuilder.buildToolListBlock(any())).thenReturn("");
    when(promptBuilder.truncateHistory(any(), anyInt())).thenReturn(session.getMessages());
    when(promptBuilder.assembleMessages(any(), any(), any(), any()))
        .thenReturn(List.of(Message.system("system prompt")));
    when(providerPort.chat(any(), any(), any())).thenReturn(toolCallResponse("http_get", "{}"));
    when(toolExecutor.execute(any(), any(), any())).thenReturn("ok");

    String reply = loop.run(session, "查天气", profile, AGENT_MD_BODY);

    verify(providerPort, times(10)).chat(any(), any(), any());
    assertTrue(reply.contains("已达到最大迭代次数"));
  }

  @Test
  @DisplayName("每轮响应和工具结果都累积进Session")
  void accumulatesMessagesInSession() {
    when(promptBuilder.buildSystemPrompt(any(), any())).thenReturn("system prompt");
    when(promptBuilder.buildMemoryBlock(any(), any())).thenReturn("");
    when(promptBuilder.buildToolListBlock(any())).thenReturn("");
    when(promptBuilder.truncateHistory(any(), anyInt())).thenReturn(session.getMessages());
    when(promptBuilder.assembleMessages(any(), any(), any(), any()))
        .thenReturn(List.of(Message.system("system prompt")));
    when(providerPort.chat(any(), any(), any()))
        .thenReturn(toolCallResponse("http_get", "{}"))
        .thenReturn(textResponse("done"));
    when(toolExecutor.execute(any(), any(), any())).thenReturn("tool result");

    loop.run(session, "hi", profile, AGENT_MD_BODY);

    List<Message> msgs = session.getMessages();
    boolean hasToolMessage = msgs.stream().anyMatch(m -> "tool".equals(m.role()));
    boolean hasAssistantMessage = msgs.stream().anyMatch(m -> "assistant".equals(m.role()));
    assertTrue(hasToolMessage, "Session should contain tool messages");
    assertTrue(hasAssistantMessage, "Session should contain assistant messages");
  }
}

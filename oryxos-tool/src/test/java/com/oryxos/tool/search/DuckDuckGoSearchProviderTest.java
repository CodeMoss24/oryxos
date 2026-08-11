package com.oryxos.tool.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oryxos.tool.sandbox.FileSandboxProperties;
import com.oryxos.tool.sandbox.HttpSandboxProperties;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import com.oryxos.tool.sandbox.ShellSandboxProperties;
import com.oryxos.tool.sandbox.WhitelistSandbox;
import java.net.http.HttpClient;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * web_search harness:DuckDuckGo Instant Answer API 解析(RelatedTopics 嵌套展平 + Abstract 兜底)、 空结果不炸、非
 * 200 抛异常,以及关键守点——域名白名单校验在请求发出之前(受限沙箱下断言 MockWebServer 一个请求都没收到)。
 */
class DuckDuckGoSearchProviderTest {

  private MockWebServer server;

  @BeforeEach
  void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("web_search:RelatedTopics 嵌套展平 + Abstract 兜底解析")
  void parsesRelatedTopicsAndAbstract() {
    String body =
        "{\"RelatedTopics\":["
            + "{\"Text\":\"topic one\",\"FirstURL\":\"http://one.example/\"},"
            + "{\"Name\":\"category\",\"Topics\":["
            + "{\"Text\":\"nested one\",\"FirstURL\":\"http://nested.example/\"},"
            + "{\"Text\":\"nested two\",\"FirstURL\":\"http://nested2.example/\"}]},"
            + "{\"Text\":\"\",\"FirstURL\":\"\"},"
            + "{\"Topics\":[{\"Text\":\"no category nested\",\"FirstURL\":\"http://cn.example/\"}]}"
            + "],"
            + "\"AbstractText\":\"abstract answer\",\"AbstractURL\":\"http://abstract.example/\"}";
    server.enqueue(new MockResponse().setBody(body));
    DuckDuckGoSearchProvider provider = provider(sandboxWithDomain("localhost"));
    List<SearchResult> results = provider.search("java");
    assertEquals(5, results.size(), "4 条 RelatedTopics(空条目跳过) + 1 条 Abstract 兜底");
    assertTrue(hasTitle(results, "topic one"), "顶层条目");
    assertTrue(hasTitle(results, "nested one"), "嵌套条目被展平");
    assertTrue(hasTitle(results, "nested two"), "嵌套条目被展平");
    assertTrue(hasTitle(results, "no category nested"), "无 Name 的嵌套分类也要展平");
    assertTrue(hasTitle(results, "abstract answer"), "AbstractText 兜底");
    assertEquals(
        "http://abstract.example/",
        results.stream()
            .filter(r -> r.title().equals("abstract answer"))
            .findFirst()
            .orElseThrow()
            .url());
  }

  @Test
  @DisplayName("web_search:空结果返回空列表,不抛异常")
  void emptyResultReturnsEmptyList() {
    server.enqueue(new MockResponse().setBody("{\"RelatedTopics\":[],\"AbstractText\":\"\"}"));
    DuckDuckGoSearchProvider provider = provider(sandboxWithDomain("localhost"));
    assertTrue(provider.search("nothing").isEmpty(), "空结果返回空列表");
  }

  @Test
  @DisplayName("web_search:HTTP 非 200 抛异常")
  void httpErrorThrows() {
    server.enqueue(new MockResponse().setResponseCode(500));
    DuckDuckGoSearchProvider provider = provider(sandboxWithDomain("localhost"));
    RuntimeException e = assertThrows(RuntimeException.class, () -> provider.search("java"));
    assertTrue(e.getMessage().contains("500"), () -> "got: " + e.getMessage());
  }

  @Test
  @DisplayName("web_search:域名白名单校验在请求发出之前,受限沙箱下请求数为 0")
  void sandboxBlocksBeforeAnyRequest() {
    server.enqueue(new MockResponse().setBody("{\"RelatedTopics\":[],\"AbstractText\":\"\"}"));
    // 空域名白名单:任何 HTTP 请求都越界——必须在校验处被拦下,不发一个请求
    DuckDuckGoSearchProvider provider = provider(sandboxWithDomain(""));
    SandboxViolationException e =
        assertThrows(SandboxViolationException.class, () -> provider.search("java"));
    assertTrue(e.getMessage().contains("不在白名单内"), () -> "got: " + e.getMessage());
    assertEquals(0, server.getRequestCount(), "白名单校验失败后不得发出任何请求");
  }

  private DuckDuckGoSearchProvider provider(Sandbox sandbox) {
    return new DuckDuckGoSearchProvider(
        sandbox, server.url("/").toString(), HttpClient.newHttpClient());
  }

  private static WhitelistSandbox sandboxWithDomain(String domain) {
    return new WhitelistSandbox(
        new FileSandboxProperties(List.of("/")),
        new ShellSandboxProperties(List.of()),
        new HttpSandboxProperties(List.of(domain)));
  }

  private static boolean hasTitle(List<SearchResult> results, String title) {
    return results.stream().anyMatch(r -> title.equals(r.title()));
  }
}

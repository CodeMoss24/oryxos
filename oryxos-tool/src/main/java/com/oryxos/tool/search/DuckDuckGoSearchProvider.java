package com.oryxos.tool.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DuckDuckGo Instant Answer API(免 key)实现:GET /?q=&lt;query&gt;&amp;format=json&amp;no_html=1, 解析
 * RelatedTopics(含嵌套 Topics 展平)与 AbstractText/AbstractURL 兜底。
 *
 * <p>search 内部第一件事过 Sandbox.enforce(HTTP_REQUEST)——域名白名单校验在请求发出之前;base URL 与 HttpClient 构造可注入(测试用
 * MockWebServer 假端点,不碰真网)。
 */
public class DuckDuckGoSearchProvider implements SearchProvider {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final Sandbox sandbox;
  private final String baseUrl;
  private final HttpClient httpClient;

  public DuckDuckGoSearchProvider(Sandbox sandbox, String baseUrl, HttpClient httpClient) {
    this.sandbox = sandbox;
    this.baseUrl = baseUrl;
    this.httpClient = httpClient;
  }

  @Override
  public List<SearchResult> search(String query) {
    String url =
        baseUrl
            + "?q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&format=json&no_html=1";
    // 第一件事过白名单:域名校验在请求发出之前(校验失败不发任何请求)
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(30))
              .GET()
              .build();
      HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        throw new RuntimeException("search failed, status: " + resp.statusCode());
      }
      return parse(resp.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("search interrupted", e);
    } catch (IOException e) {
      throw new RuntimeException("search failed: " + e.getMessage(), e);
    }
  }

  private static List<SearchResult> parse(String body) throws IOException {
    Map<String, Object> root = objectMapper.readValue(body, Map.class);
    List<SearchResult> results = new ArrayList<>();
    Object related = root.get("RelatedTopics");
    if (related instanceof List<?> topics) {
      for (Object o : topics) {
        if (!(o instanceof Map<?, ?> m)) continue;
        Object nested = m.get("Topics");
        if (nested instanceof List<?> nestedList) {
          for (Object no : nestedList) {
            addTopic(no, results);
          }
        } else {
          addTopic(o, results);
        }
      }
    }
    // AbstractText/AbstractURL 兜底(直接答案场景 RelatedTopics 可能为空)
    String abstractText = str(root.get("AbstractText"));
    if (!abstractText.isBlank()) {
      results.add(new SearchResult(abstractText, str(root.get("AbstractURL")), abstractText));
    }
    return results;
  }

  private static void addTopic(Object o, List<SearchResult> results) {
    if (!(o instanceof Map<?, ?> m)) return;
    String text = str(m.get("Text"));
    String url = str(m.get("FirstURL"));
    if (text.isBlank() && url.isBlank()) {
      return; // 空条目跳过
    }
    results.add(new SearchResult(text, url, text));
  }

  private static String str(Object o) {
    return o == null ? "" : String.valueOf(o);
  }
}

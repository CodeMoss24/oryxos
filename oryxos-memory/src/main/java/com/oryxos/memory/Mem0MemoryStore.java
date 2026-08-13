package com.oryxos.memory;

import com.oryxos.core.memory.MemoryScope;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Mem0 档(外部集成):接一个自托管 Mem0 记忆层(数据不出域),本类只做协议转换——把 append/load/ recall 翻译成 Mem0 的 REST
 * 调用。提炼、冲突消解、语义检索都交给 Mem0 自己管。
 *
 * <p>凭证与地址走环境变量占位(宪法:不落明文)。端点形态以部署的 mem0 版本为准,自动化测试不碰真 server(契约测试用内存假替身,真实 REST 交互由
 * Mem0MemoryStoreTest 单独 mock 验证)。
 */
@Component
@ConditionalOnProperty(name = "oryxos.memory.backend", havingValue = "mem0")
public class Mem0MemoryStore implements LongTermMemoryStore {

  private final RestClient restClient;
  private final String userId;

  public Mem0MemoryStore(
      RestClient restClient,
      @Value("${MEM0_BASE_URL:}") String baseUrl,
      @Value("${MEM0_USER_ID:default}") String userId) {
    this.restClient = baseUrl.isBlank() ? restClient : restClient.mutate().baseUrl(baseUrl).build();
    this.userId = userId;
  }

  @Override
  public void append(String content, MemoryScope scope) {
    // Mem0 的 add:它自己做提炼与冲突消解,scope 落进 metadata 供检索区分
    // TODO(24节):涉外 HTTP IO 首行应过 Sandbox.enforce(HTTP_REQUEST, MEM0_BASE_URL),Sandbox 就位后接线
    restClient
        .post()
        .uri("/v1/memories/")
        .body(
            Map.of(
                "messages", List.of(Map.of("role", "user", "content", content)),
                "user_id", userId,
                "metadata", Map.of("scope", scope.name())))
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public String load() {
    // 核心记忆按 scope 过滤全量取,归档区取最近若干(分页参数以部署的 mem0 版本为准)
    String core = getByScope("CORE");
    String archival = getByScope("ARCHIVAL");
    return (core + "\n" + archival).trim();
  }

  @Override
  public String readAll() {
    // 全量原样读取:不带 scope/分页参数,取该用户全部记忆(注入视图的截断策略保留在 load())
    return getRaw("/v1/memories/?user_id=" + userId);
  }

  @Override
  public List<String> recallByKeyword(String keyword) {
    // Mem0 的 search 是语义检索,比关键词强——契约四的"加强版实现"
    String raw = getRaw("/v1/memories/search?query=" + keyword + "&user_id=" + userId);
    if (raw == null || raw.isBlank()) return List.of();
    return Arrays.stream(raw.split("\\n")).filter(line -> !line.isBlank()).toList();
  }

  /** 按 scope 取记忆。响应为 Mem0 返回的原始文本(JSON 或纯文本,以部署版本为准)。 */
  private String getByScope(String scope) {
    return getRaw("/v1/memories/?user_id=" + userId + "&scope=" + scope);
  }

  private String getRaw(String uri) {
    // TODO(24节):涉外 HTTP IO 首行应过 Sandbox.enforce(HTTP_REQUEST, ...),Sandbox 就位后接线
    return restClient.get().uri(uri).retrieve().toEntity(String.class).getBody();
  }
}

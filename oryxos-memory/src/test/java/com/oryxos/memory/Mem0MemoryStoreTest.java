package com.oryxos.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oryxos.core.memory.MemoryScope;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Mem0 档:mock RestClient 验证请求形态——append 请求体带 scope、recall 转发查询。不碰真 server。 */
@DisplayName("Mem0MemoryStore — 请求形态(mock RestClient,不碰真 server)")
class Mem0MemoryStoreTest {

  private RestClient restClient;
  private RestClient.RequestBodyUriSpec postUri;
  private RestClient.RequestBodySpec postBody;
  private RestClient.ResponseSpec postResponse;
  private RestClient.RequestHeadersUriSpec getUri;
  private RestClient.RequestHeadersSpec getSpec;
  private RestClient.ResponseSpec getResponse;

  @BeforeEach
  @SuppressWarnings({"rawtypes", "unchecked"})
  void setUpMocks() {
    restClient = mock(RestClient.class);
    postUri = mock(RestClient.RequestBodyUriSpec.class);
    postBody = mock(RestClient.RequestBodySpec.class);
    postResponse = mock(RestClient.ResponseSpec.class);
    getUri = mock(RestClient.RequestHeadersUriSpec.class);
    getSpec = mock(RestClient.RequestHeadersSpec.class);
    getResponse = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(postUri);
    when(postUri.uri(anyString())).thenReturn(postBody);
    // any() 是 <T> T 泛型方法,单参会重载到更具体的 body(StreamingHttpOutputMessage.Body);
    // 显式 any(Map.class) 锁定 body(Object) 重载(Mem0 传的是 Map)。
    when(postBody.body(any(Map.class))).thenReturn(postBody);
    when(postBody.retrieve()).thenReturn(postResponse);
    when(postResponse.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

    when(restClient.get()).thenReturn(getUri);
    when(getUri.uri(anyString())).thenReturn(getSpec);
    when(getSpec.retrieve()).thenReturn(getResponse);
  }

  @Test
  @DisplayName("append发出的请求体带scope进metadata")
  @SuppressWarnings({"rawtypes", "unchecked"})
  void appendRequestCarriesScopeInMetadata() {
    var store = new Mem0MemoryStore(restClient, "", "user-1");
    store.append("偏好 Java", MemoryScope.CORE);

    ArgumentCaptor<Map> bodyCaptor = ArgumentCaptor.forClass(Map.class);
    verify(postBody).body(bodyCaptor.capture());
    Map body = bodyCaptor.getValue();
    assertThat(body.get("user_id")).isEqualTo("user-1");
    assertThat(((Map) body.get("metadata")).get("scope")).isEqualTo("CORE");
  }

  @Test
  @DisplayName("recall转发search查询并解析响应行")
  void recallForwardsSearchQuery() {
    when(getResponse.toEntity(String.class)).thenReturn(ResponseEntity.ok("命中1\n命中2"));
    var store = new Mem0MemoryStore(restClient, "", "user-1");

    assertThat(store.recallByKeyword("Java")).containsExactly("命中1", "命中2");
    verify(getUri).uri("/v1/memories/search?query=Java&user_id=user-1");
  }

  @Test
  @DisplayName("recall未命中返回空列表不抛异常")
  void recallMissReturnsEmptyList() {
    when(getResponse.toEntity(String.class)).thenReturn(ResponseEntity.ok(""));
    var store = new Mem0MemoryStore(restClient, "", "user-1");
    assertThat(store.recallByKeyword("航天")).isEmpty();
  }

  @Test
  @DisplayName("load按CORE与ARCHIVAL两个scope分别取")
  void loadFetchesBothScopes() {
    when(getResponse.toEntity(String.class))
        .thenReturn(ResponseEntity.ok("核心内容"))
        .thenReturn(ResponseEntity.ok("归档内容"));
    var store = new Mem0MemoryStore(restClient, "", "user-1");

    String loaded = store.load();

    assertThat(loaded).contains("核心内容").contains("归档内容");
    verify(getUri).uri("/v1/memories/?user_id=user-1&scope=CORE");
    verify(getUri).uri("/v1/memories/?user_id=user-1&scope=ARCHIVAL");
  }
}

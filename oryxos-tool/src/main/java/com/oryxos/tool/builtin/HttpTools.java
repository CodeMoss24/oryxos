package com.oryxos.tool.builtin;

import com.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * HTTP 内置 Tool:http_get / http_post,带域名白名单。
 *
 * <p>普通方法由 ToolConfiguration 装配成工具;执行第一件事调 Sandbox.enforce(HTTP_REQUEST) 做域名白名单 检查,校验不过不发任何请求。
 */
@Component
public class HttpTools {

  private final Sandbox sandbox;
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public HttpTools(Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  public String httpGet(String url) throws IOException, InterruptedException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
    HttpRequest req =
        HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    return resp.body();
  }

  public String httpPost(String url, String body) throws IOException, InterruptedException {
    sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
    HttpRequest req =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    return resp.body();
  }
}

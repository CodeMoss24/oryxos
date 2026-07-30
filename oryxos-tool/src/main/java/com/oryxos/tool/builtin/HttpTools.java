package com.oryxos.tool.builtin;

import com.oryxos.core.tool.OryxTool;
import com.oryxos.core.tool.ToolResult;
import com.oryxos.tool.sandbox.Sandbox;
import com.oryxos.tool.sandbox.SandboxViolationException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP 内置 Tool:http_get / http_post,带域名白名单。
 */
@Component
public class HttpTools {

    private final Sandbox sandbox;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public HttpTools(Sandbox sandbox) {
        this.sandbox = sandbox;
    }

    @Component("http_get")
    public class HttpGetTool implements OryxTool {
        @Override public String getName() { return "http_get"; }
        @Override public String getDescription() { return "发起 HTTP GET 请求(受域名白名单限制)"; }
        @Override public String getInputSchema() {
            return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}";
        }
        @Override public ToolResult execute(String inputJson) {
            String url = FileTools.extractField(inputJson, "url");
            try {
                sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                return ToolResult.success(resp.body());
            } catch (SandboxViolationException e) {
                return ToolResult.failure(e.getMessage(), false);
            } catch (Exception e) {
                return ToolResult.failure(e.getMessage(), true);
            }
        }
    }

    @Component("http_post")
    public class HttpPostTool implements OryxTool {
        @Override public String getName() { return "http_post"; }
        @Override public String getDescription() { return "发起 HTTP POST 请求(受域名白名单限制)"; }
        @Override public String getInputSchema() {
            return "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},"
                    + "\"body\":{\"type\":\"string\"}},\"required\":[\"url\",\"body\"]}";
        }
        @Override public ToolResult execute(String inputJson) {
            String url = FileTools.extractField(inputJson, "url");
            String body = FileTools.extractField(inputJson, "body");
            try {
                sandbox.enforce(new Sandbox.SandboxAction(Sandbox.ActionType.HTTP_REQUEST, url));
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                return ToolResult.success(resp.body());
            } catch (SandboxViolationException e) {
                return ToolResult.failure(e.getMessage(), false);
            } catch (Exception e) {
                return ToolResult.failure(e.getMessage(), true);
            }
        }
    }
}

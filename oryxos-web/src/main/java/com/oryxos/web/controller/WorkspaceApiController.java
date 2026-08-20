package com.oryxos.web.controller;

import com.oryxos.core.agent.AgentLifecycleService;
import com.oryxos.core.agent.AgentStore;
import com.oryxos.web.dto.ApiResponse;
import com.oryxos.web.dto.FileNode;
import com.oryxos.web.exception.InvalidRequestException;
import com.oryxos.web.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作区文件浏览与编辑(第 30 节 5.2.3)。
 *
 * <p>tree 返回 agents/ + archive/ 的目录树;file 读/写复用 {@link AgentStore#resolveWorkspacePath} 的防目录穿越
 * (normalize 后 startsWith 工作区根,越界 400——本控制器唯一的安全要点)。编辑 agents/&lt;name&gt;/AGENT.md 走 {@link
 * AgentLifecycleService#update}(写 + 校验 + 重注册):WatchService 不监听子目录内文件改动,必须显式重注册。
 */
@RestController
@RequestMapping("/api/v1/workspace")
public class WorkspaceApiController {

  private final AgentStore agentStore;
  private final AgentLifecycleService lifecycle;

  public WorkspaceApiController(AgentStore agentStore, AgentLifecycleService lifecycle) {
    this.agentStore = agentStore;
    this.lifecycle = lifecycle;
  }

  /** 目录树:agents/(每 Agent 一目录,可展开) + archive/;不存在的段不出现。 */
  @GetMapping("/tree")
  public ApiResponse<FileNode> tree() {
    List<FileNode> children = new ArrayList<>();
    FileNode agents = buildTree("agents");
    FileNode archive = buildTree("archive");
    if (agents != null) {
      children.add(agents);
    }
    if (archive != null) {
      children.add(archive);
    }
    return ApiResponse.ok(new FileNode(".oryxos", "", "dir", children));
  }

  /** 读文件文本内容;防目录穿越,越界或非文件 → 400。 */
  @GetMapping("/file")
  public ApiResponse<Map<String, String>> readFile(@RequestParam("path") String path) {
    Path target = agentStore.resolveWorkspacePath(path);
    if (!Files.isRegularFile(target)) {
      throw new InvalidRequestException("not a file: " + path);
    }
    try {
      return ApiResponse.ok(Map.of("path", path, "content", Files.readString(target)));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read file: " + path, e);
    }
  }

  /** 写文件;agents/&lt;name&gt;/AGENT.md 走 lifecycle.update(写+校验+重注册),其余直接写盘。 */
  @PostMapping("/file")
  public ApiResponse<Map<String, String>> writeFile(@RequestBody Map<String, String> body) {
    String path = body.getOrDefault("path", "");
    String content = body.getOrDefault("content", "");
    Path target = agentStore.resolveWorkspacePath(path); // 防穿越校验先行,越界 400 不落盘
    if (isAgentMd(path)) {
      lifecycle.update(path.split("/")[1], content);
    } else {
      try {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to write file: " + path, e);
      }
    }
    return ApiResponse.ok(Map.of("message", "file saved: " + path));
  }

  /**
   * 下载文件(二进制附件流):把 Agent {@code output/} 里的研报 / 汇总 / 导出等产出下载到本地。防目录穿越同 {@link #readFile}:越界 →
   * 400,不存在 → 404。这里带 Content-Disposition attachment、按内容类型返回原始字节,任意文件类型都能下。
   */
  @GetMapping("/download")
  public ResponseEntity<Resource> download(@RequestParam("path") String path) {
    Path target = agentStore.resolveWorkspacePath(path);
    if (!Files.isRegularFile(target)) {
      throw new ResourceNotFoundException("文件不存在: " + path); // → 404
    }
    String filename = String.valueOf(target.getFileName());
    // 文件名可能含中文/空格:用 RFC 5987 编码进 Content-Disposition,避免乱码或截断
    String disposition =
        ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString();
    MediaType contentType;
    long length;
    try {
      String probed = Files.probeContentType(target);
      contentType =
          probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;
      length = Files.size(target);
    } catch (IOException e) {
      throw new UncheckedIOException("读取文件失败: " + path, e);
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .contentType(contentType)
        .contentLength(length)
        .body(new FileSystemResource(target));
  }

  /** agents/&lt;name&gt;/AGENT.md 恰好两段路径(直接子文件)才算"编辑 Agent 定义"。 */
  private static boolean isAgentMd(String path) {
    return path.startsWith("agents/") && path.endsWith("/AGENT.md");
  }

  private FileNode buildTree(String sub) {
    Path dir = agentStore.resolveWorkspacePath(sub);
    if (!Files.isDirectory(dir)) {
      return null;
    }
    try (Stream<Path> stream = Files.list(dir)) {
      List<FileNode> children =
          stream
              .sorted()
              .map(
                  p -> {
                    String childRel = sub + "/" + p.getFileName();
                    return Files.isDirectory(p)
                        ? buildRecursive(p, childRel)
                        : new FileNode(p.getFileName().toString(), childRel, "file", List.of());
                  })
              .toList();
      return new FileNode(sub, sub, "dir", children);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list workspace dir: " + sub, e);
    }
  }

  private FileNode buildRecursive(Path dir, String rel) {
    try (Stream<Path> stream = Files.list(dir)) {
      List<FileNode> children =
          stream
              .sorted()
              .map(
                  p -> {
                    String childRel = rel + "/" + p.getFileName();
                    return Files.isDirectory(p)
                        ? buildRecursive(p, childRel)
                        : new FileNode(p.getFileName().toString(), childRel, "file", List.of());
                  })
              .toList();
      return new FileNode(dir.getFileName().toString(), rel, "dir", children);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to list workspace dir: " + rel, e);
    }
  }
}

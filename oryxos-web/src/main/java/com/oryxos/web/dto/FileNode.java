package com.oryxos.web.dto;

import java.util.List;

/**
 * 工作区目录树节点(第 30 节 5.2.3 文件浏览器):name + 工作区相对 path + 类型(dir/file) + 子节点。
 *
 * <p>前端按 type 渲染目录/文件,按 path 调 GET /api/v1/workspace/file 读取内容。
 */
public record FileNode(String name, String path, String type, List<FileNode> children) {}

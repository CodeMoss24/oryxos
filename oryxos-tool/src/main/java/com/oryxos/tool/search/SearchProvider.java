package com.oryxos.tool.search;

import java.util.List;

/**
 * 搜索引擎统一抽象(web_search 工具的后端)。接口先行——核心阶段只实现免 key 的 DuckDuckGo, 未来换/加引擎不改调用方。
 *
 * <p>实现约定:发请求前必须先过 Sandbox.enforce(HTTP_REQUEST, 实际请求 URL)——域名白名单校验发生在 provider 内部、请求发出之前,与
 * http_get 共用同一 WhitelistSandbox 的 http.allowed-domains。
 */
public interface SearchProvider {

  List<SearchResult> search(String query);
}

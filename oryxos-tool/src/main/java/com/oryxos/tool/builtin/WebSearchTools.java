package com.oryxos.tool.builtin;

import com.oryxos.tool.search.SearchProvider;
import com.oryxos.tool.search.SearchResult;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 搜索内置 Tool:web_search。委托 SearchProvider 抽象检索,把结果渲染成模型可读文本(标题/URL/摘要)。 域名白名单校验在 provider
 * 内部、请求发出之前完成(见 SearchProvider 约定)。
 */
@Component
public class WebSearchTools {

  private final SearchProvider searchProvider;

  public WebSearchTools(SearchProvider searchProvider) {
    this.searchProvider = searchProvider;
  }

  public String webSearch(String query) {
    List<SearchResult> results = searchProvider.search(query);
    StringBuilder sb = new StringBuilder();
    for (SearchResult r : results) {
      sb.append(r.title())
          .append("\n")
          .append(r.url())
          .append("\n")
          .append(r.snippet())
          .append("\n\n");
    }
    return sb.toString();
  }
}

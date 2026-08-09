package com.oryxos.tool.search;

/** 一条搜索结果:标题 / URL / 摘要。 */
public record SearchResult(String title, String url, String snippet) {}

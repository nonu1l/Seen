package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchResultDTO;

/**
 * Web 搜索源策略，供统一搜索入口按运行时设置选择具体 provider。
 */
public interface WebSearchProvider {

    /**
     * @return provider 配置值，例如 serper 或 tavily。
     */
    String providerKey();

    /**
     * @return 当前搜索源是否具备调用条件，通常取决于 API Key 是否已配置。
     */
    boolean isAvailable();

    /**
     * 执行搜索并返回给 Agent 可理解的结果或失败原因。
     *
     * @param query 搜索关键词
     * @return 结构化搜索诊断结果
     */
    WebSearchResultDTO searchWithDiagnostics(String query);
}

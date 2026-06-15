package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;

import java.util.List;

/**
 * Web 搜索能力抽象，屏蔽不同搜索服务商差异。
 */
public interface SearchProvider {
    /**
     * 根据关键词执行搜索。
     *
     * @param query 搜索关键词
     * @return 标准化搜索结果
     */
    List<WebSearchItem> search(String query);

    /**
     * 拉取并返回正文文本。
     *
     * @param url 目标 URL
     * @return 清洗后的文本
     */
    String fetch(String url);
}

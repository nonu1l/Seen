package com.nonu1l.media.agent.tool;

import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.WatchSourceResultDTO;
import com.nonu1l.media.service.WatchSourceSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 在线观看地址搜索工具。
 */
@Component
public class AiWatchSourceTools {

    private static final Logger log = LoggerFactory.getLogger(AiWatchSourceTools.class);

    private final WatchSourceSearchService watchSourceSearchService;

    /**
     * 创建片源搜索工具。
     *
     * @param watchSourceSearchService 片源搜索服务
     */
    public AiWatchSourceTools(WatchSourceSearchService watchSourceSearchService) {
        this.watchSourceSearchService = watchSourceSearchService;
    }

    /**
     * 根据用户原始问题搜索候选观看地址。
     *
     * @param query 用户原始问题或片名
     * @return 候选观看地址结果
     */
    public WatchSourceResultDTO searchWatchSources(String query) {
        log.debug("Tool: searchWatchSources query='{}'", query);
        TokenUsageAdvisor.setCurrentNode("tool-searchWatchSources");
        try {
            return watchSourceSearchService.search(query);
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }
}

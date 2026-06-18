package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.CompactResultDTO;
import com.nonu1l.media.model.dto.CompactSubjectDTO;
import com.nonu1l.media.model.dto.WorkSearchResultDTO;
import com.nonu1l.media.service.BangumiService;
import com.nonu1l.media.service.SearchResultPreprocessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 专用 Bangumi 工具，返回适合模型消费的紧凑结果。
 */
@Component
public class AiBangumiTools {

    private static final Logger log = LoggerFactory.getLogger(AiBangumiTools.class);

    private final BangumiService bangumiService;
    private final SearchResultPreprocessor preprocessor;

    /**
     * @param bangumiService Bangumi 底层服务
     * @param preprocessor 搜索结果预处理器
     */
    public AiBangumiTools(BangumiService bangumiService, SearchResultPreprocessor preprocessor) {
        this.bangumiService = bangumiService;
        this.preprocessor = preprocessor;
    }

    /**
     * 搜索 Bangumi 并压缩为 AI 工具结果。
     *
     * @param keyword 关键词
     * @return 紧凑条目列表
     */
    public List<CompactResultDTO> searchBangumi(String keyword) {
        log.debug("Tool: searchBangumi keyword='{}'", keyword);
        List<WorkSearchResultDTO> raw = bangumiService.search(keyword);

        List<CompactSubjectDTO> compact = preprocessor.preprocess(raw);
        return compact.stream().map(CompactResultDTO::from).toList();
    }

    /**
     * 搜索 Bangumi 并返回最靠前的一条紧凑结果。
     *
     * @param keyword 关键词
     * @return 命中的紧凑条目，未命中返回 null
     */
    public CompactResultDTO searchBangumiOneResult(String keyword) {
        log.debug("Tool: searchBangumiOneResult keyword='{}'", keyword);
        List<WorkSearchResultDTO> raw = bangumiService.search(keyword, 1);
        if (raw.isEmpty()) {
            return null;
        }
        List<CompactSubjectDTO> compact = preprocessor.preprocess(raw);
        if (compact.isEmpty()) {
            return null;
        }
        return compact.stream().map(CompactResultDTO::from).toList().getFirst();
    }

}

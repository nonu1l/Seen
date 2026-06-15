package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.CompactResult;
import com.nonu1l.media.model.dto.CompactSubject;
import com.nonu1l.media.model.dto.LocalRecord;
import com.nonu1l.media.model.dto.WebSearchItem;
import com.nonu1l.media.model.dto.WorkSearchResult;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Agent 工具集 — 通过 FunctionToolCallback 注册，让 LLM 自主调用。
 */
@Component
public class BangumiTools {

    private static final Logger log = LoggerFactory.getLogger(BangumiTools.class);

    private final BangumiService bangumiService;
    private final SearchResultPreprocessor preprocessor;
    private final RecordRepository recordRepo;
    private final WorkRepository workRepo;
    private final WebSearchService webSearchService;

    /**
     * @param bangumiService Bangumi 业务服务
     * @param preprocessor 搜索结果预处理器
     * @param recordRepo 记录仓储
     * @param workRepo 作品仓储
     * @param webSearchService Web 搜索聚合服务
     */
    public BangumiTools(BangumiService bangumiService, SearchResultPreprocessor preprocessor,
                         RecordRepository recordRepo, WorkRepository workRepo,
                         WebSearchService webSearchService) {
        this.bangumiService = bangumiService;
        this.preprocessor = preprocessor;
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
        this.webSearchService = webSearchService;
    }

    /**
     * 搜索 Bangumi
     *
     * @param keyword 关键词
     * @return {@link List }<{@link CompactResult }>
     */
    public List<CompactResult> searchBangumi(String keyword) {
        log.debug("Tool: searchBangumi keyword='{}'", keyword);
        List<WorkSearchResult> raw = bangumiService.search(keyword);

        List<CompactSubject> compact = preprocessor.preprocess(raw, keyword);
        return compact.stream().map(CompactResult::from).toList();
    }

    /**
     * 搜索 Bangumi 一个结果
     *
     * @param keyword 关键词
     * @return {@link CompactResult }
     */
    public CompactResult searchBangumiOneResult(String keyword) {
        log.debug("Tool: searchBangumiOneResult keyword='{}'", keyword);
        List<WorkSearchResult> raw = bangumiService.search(keyword, 1);
        if (raw.isEmpty()) {
            return null;
        }
        List<CompactSubject> compact = preprocessor.preprocess(raw, keyword);
        if (compact.isEmpty()) {
            return null;
        }
        return compact.stream().map(CompactResult::from).toList().getFirst();
    }

    /**
     * 搜索本地数据库中的作品。
     *
     * <p>返回有最新记录的本地条目，未有记录的作品不会展示为已追踪状态。</p>
     *
     * @param keyword 关键词，空白时返回全部
     * @return 紧凑本地记录列表
     */
    public List<LocalRecord> searchLocal(String keyword) {
        log.debug("Tool: searchLocal keyword='{}'", keyword);
        List<Work> works = (keyword == null || keyword.isBlank())
                ? workRepo.findAll()
                : workRepo.searchByName(keyword.trim());

        List<LocalRecord> results = new ArrayList<>();
        for (Work w : works) {
            Optional<Record> r = recordRepo.findLatestByWorkId(w.getId());
            r.ifPresent(record -> results.add(new LocalRecord(
                    w.getId(), w.getNameCn() != null ? w.getNameCn() : w.getName(),
                    record.getStatus(),
                    record.getRating() != null ? record.getRating().intValue() : null,
                    record.getReview())));
        }
        return results;
    }

    /**
     * 走统一 Web 搜索入口进行外部检索。
     *
     * @param query 检索关键词
     * @return 外部检索结果
     */
    public List<WebSearchItem> searchWeb(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.search(query);
    }

    /**
     * 抓取 Web 页面正文文本。
     *
     * @param url 要抓取的 URL
     * @return 清洗后的文本片段，失败时可能为 {@code null}
     */
    public String fetchWeb(String url) {
        log.debug("Tool: fetchWeb url='{}'", url);
        return webSearchService.fetch(url);
    }

    /**
     * 获取 Bangumi 热门排行（已废弃，保留兼容）。
     *
     * @param type 条目类型
     * @param year 年份过滤，可为空
     * @return 紧凑排行结果
     */
    @Deprecated
    public List<CompactResult> trendingBangumi(int type, Integer year) {
        log.debug("Tool: trendingBangumi type={} year={}", type, year);
        return bangumiService.trending(type, year).stream()
                .map(r -> new CompactResult(
                        r.getId(), r.getNameCn(), r.getNameOrig(),
                        r.getPlatform(), r.getAirDate(),
                        r.getEpsCount(), r.getScore(), r.getRank()))
                .toList();
    }
}

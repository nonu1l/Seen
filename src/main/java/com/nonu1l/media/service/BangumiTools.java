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

        //对结果进行按照RANK排序
        List<CompactSubject> compact = preprocessor.preprocess(raw, keyword);
        return compact.stream().map(CompactResult::from).toList();
    }

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

    public List<WebSearchItem> searchWeb(String query) {
        log.debug("Tool: searchWeb query='{}'", query);
        return webSearchService.search(query);
    }

    public String fetchWeb(String url) {
        log.debug("Tool: fetchWeb url='{}'", url);
        return webSearchService.fetch(url);
    }

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

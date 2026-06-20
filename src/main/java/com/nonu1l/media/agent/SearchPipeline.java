package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AiBangumiTools;
import com.nonu1l.media.agent.tool.AiWebSearchTools;
import com.nonu1l.media.model.dto.FindWorksCandidateDTO;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.service.AiChatCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多步搜索流水线：生成搜索关键词、抓取页面、抽取标题、Bangumi 匹配并做结果校验。
 *
 * 流程：
 * 1. 生成 3 组搜索关键词
 * 2. 依次搜索 → 多线程抓取 → LLM 提炼片名 → 多线程 Bangumi 匹配
 * 3. 可用结果提前返回；全空则通过 LLM 生成失败原因
 */
@Service
public class SearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(SearchPipeline.class);

    private final AiChatCallService AiChatCallService;
    private final AiBangumiTools bangumiTools;
    private final AiWebSearchTools webSearchTools;
    private final int maxCards;
    private final int maxPages;
    private final int maxDirectUrls;
    private final int keywordLimit;
    private final int bangumiRetryLimit;
    private final int directFetchMaxChars;
    private final int extractTextMaxChars;
    private final String promptKeywords;
    private final String promptTitles;
    private final String promptValidate;
    private final String promptFail;

    /**
     * @param AiChatCallService LLM 调用服务
     * @param bangumiTools AI Bangumi 查询工具
     * @param webSearchTools AI Web 搜索工具
     * @param maxCards 最多聚合的候选卡片数量
     * @param maxPages 每轮关键词最多抓取的搜索结果页面数量
     * @param maxDirectUrls 搜索无结果时最多直访的公开 URL 数量
     */
    public SearchPipeline(AiChatCallService AiChatCallService,
                          AiBangumiTools bangumiTools,
                          AiWebSearchTools webSearchTools,
                          @Value("${app.runtime.search-pipeline.max-cards:5}") int maxCards,
                          @Value("${app.runtime.search-pipeline.max-pages:10}") int maxPages,
                          @Value("${app.runtime.search-pipeline.max-direct-urls:3}") int maxDirectUrls,
                          @Value("${app.runtime.search-pipeline.keyword-limit:3}") int keywordLimit,
                          @Value("${app.runtime.search-pipeline.bangumi-retry-limit:3}") int bangumiRetryLimit,
                          @Value("${app.runtime.search-pipeline.direct-fetch-max-chars:6000}") int directFetchMaxChars,
                          @Value("${app.runtime.search-pipeline.extract-text-max-chars:8000}") int extractTextMaxChars) {
        this.AiChatCallService = AiChatCallService;
        this.bangumiTools = bangumiTools;
        this.webSearchTools = webSearchTools;
        this.maxCards = maxCards;
        this.maxPages = maxPages;
        this.maxDirectUrls = maxDirectUrls;
        this.keywordLimit = keywordLimit;
        this.bangumiRetryLimit = bangumiRetryLimit;
        this.directFetchMaxChars = directFetchMaxChars;
        this.extractTextMaxChars = extractTextMaxChars;
        this.promptKeywords = load("prompts/pipeline-keywords.st");
        this.promptTitles = load("prompts/pipeline-titles.st");
        this.promptValidate = load("prompts/pipeline-validate.st");
        this.promptFail = load("prompts/pipeline-fail.st");
    }

    private static String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }

    /**
     * 搜索流水线执行结果。
     *
     * @param cards 命中的作品列表，通常最多 maxCards 条
     * @param failReason 全空时可见失败原因
     */
    public record PipelineResult(List<FindWorksCandidateDTO> cards, String failReason) {}

    // ── 主入口 ──

    /**
     * 不带历史上下文的搜索入口，内部自动透传空历史。
     *
     * @param userInput 用户检索意图
     * @return 流水线执行结果
     */
    public PipelineResult execute(String userInput) {
        return execute(userInput, "");
    }

    /**
     * 执行一次推荐/搜索流水线：先生成关键词，循环查询每组关键词并并发抓取与匹配。
     *
     * @param userInput 当前用户输入
     * @param history 会话历史（用于提升标题提炼准确率）
     * @return 命中卡片或失败原因
     */
    public PipelineResult execute(String userInput, String history) {
        return execute(userInput, history, AgentRunEvents.noop());
    }

    /**
     * 执行一次推荐/搜索流水线，并在关键步骤发送简洁状态。
     *
     * @param userInput 当前用户输入
     * @param history 会话历史（用于提升标题提炼准确率）
     * @param listener 单轮 Agent 监听器
     * @return 命中卡片或失败原因
     */
    public PipelineResult execute(String userInput, String history, AgentRunListener listener) {
        AgentRunListener runListener = listener != null ? listener : AgentRunEvents.noop();
        String context = history.isEmpty() ? userInput : "对话历史：\n" + history + "\n当前请求：" + userInput;
        runListener.status("正在生成搜索关键词");
        List<String> keywords = generateKeywords(context);
        if (keywords.isEmpty()) {
            runListener.status("正在生成失败说明");
            return new PipelineResult(List.of(), failMessage(userInput, "关键词生成失败"));
        }

        List<FindWorksCandidateDTO> allCards = new ArrayList<>();
        var seenSubjectIds = new HashSet<Long>();
        int tried = 0;

        for (String kw : keywords) {
            log.info("Pipeline: trying keyword '{}'", kw);
            runListener.status("正在搜索作品资料");
            tried++;

            // 2a. 搜索
            List<WebSearchItemDTO> webResults = webSearchTools.searchWebItems(kw);

            // 2b. 多线程抓取 + 清洗
            runListener.status("正在读取搜索结果");
            List<String> pageTexts = webResults.isEmpty()
                    ? fetchDirectUrls(context, kw)
                    : webResults.stream().limit(Math.max(1, maxPages)).parallel()
                            .map(r -> webSearchTools.fetchWebText(r.url()))
                            .filter(t -> t != null && !t.isBlank())
                            .toList();
            if (pageTexts.isEmpty()) continue;

            // 2c. LLM 提炼片名
            runListener.status("正在整理候选片名");
            List<String> titles = extractTitles(String.join("\n---\n", pageTexts), context);
            if (titles.isEmpty()) continue;

            // 2d. 并发搜索→title→card映射，剔除空匹配和重复 subjectId
            // 使用原 .parallel() 对Map结果映射同时进行去重，过滤空值
            // 可能会与 LangGraph4j（共用ForkJoinPool.commonPool） 冲突引发 NullPointerException 问题

            var futures = titles.stream().distinct()
                    .collect(Collectors.toMap(
                            t -> t, t -> CompletableFuture.supplyAsync(() ->
                                    searchBangumiWithRetry(t, Math.max(1, bangumiRetryLimit)))));

            runListener.status("正在查询 Bangumi");
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
            var cardMap = new java.util.LinkedHashMap<String, FindWorksCandidateDTO>();
            futures.forEach((t, f) -> { var c = f.join(); if (c != null) cardMap.put(t, c); });
            cardMap.values().removeIf(Objects::isNull);
            var seen = new HashSet<Long>();
            cardMap.values().removeIf(c -> !seen.add(c.subjectId()));
            // 跨关键词去重
            cardMap.values().removeIf(c -> !seenSubjectIds.add(c.subjectId()));

            // 校验匹配结果
            if (!cardMap.isEmpty()) {
                runListener.status("正在校验候选作品");
                var validIds = validateMatchIds(cardMap, context);
                cardMap.values().removeIf(c -> !validIds.contains(c.subjectId()));
            }

            allCards.addAll(cardMap.values());
            log.info("Pipeline: keyword '{}' → {} cards", kw, cardMap.size());

            if (allCards.size() >= Math.max(1, maxCards)) break;
        }

        if (allCards.isEmpty()) {
            runListener.status("正在生成失败说明");
            return new PipelineResult(List.of(),
                    failMessage(context, "已尝试 " + tried + " 组关键词，均未找到匹配的影视作品"));
        }
        return new PipelineResult(allCards, null);
    }

    /**
     * 按关键词重试搜索 Bangumi 单条结果，命中后立即返回第一条。
     *
     * @param keyword 查询关键字
     * @param maxRetries 最大重试次数
     * @return 命中条目；失败则返回 null
     */
    FindWorksCandidateDTO searchBangumiWithRetry(String keyword, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                var first = bangumiTools.searchBangumiOneResult(keyword);
                if (first != null) {
                    return new FindWorksCandidateDTO(first.id(), first.nameCn(), null, null, null, null,
                            first.airDate());
                }
            } catch (Exception e) {
                log.warn("searchBangumi '{}' failed (attempt {}/{}): {}", keyword, i + 1, maxRetries, e.getMessage());
            }
            if (i < maxRetries - 1) {
                try { Thread.sleep(500L * (i + 1)); } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    /**
     * 用 LLM 校验标题与 Bangumi 候选映射，过滤误匹配。
     *
     * @param cardMap 标题到匹配卡片映射
     * @param context 归一化后的上下文
     * @return 通过校验的 subjectId 集合；若 LLM 返回空则退化为不变更原集合
     */
    java.util.Set<Long> validateMatchIds(Map<String, FindWorksCandidateDTO> cardMap, String context) {
        if (cardMap.isEmpty()) return java.util.Set.of();
        StringBuilder sb = new StringBuilder();
        cardMap.forEach((title, c) -> sb.append(title).append(" → ").append(c.nameCn())
            .append(" (id=").append(c.subjectId())
            .append(c.airDate() != null ? ", 日期=" + c.airDate() : "")
            .append(")\n"));
        String prompt = promptValidate.replace("{today}", LocalDate.now().toString())
                .replace("{context}", context);
        String result = AiChatCallService.task()
                .node("pipeline-validateMatch")
                .system(prompt)
                .user(sb.toString())
                .call();
        if (result == null || result.isBlank()) {
            return new java.util.HashSet<>(cardMap.values().stream().map(FindWorksCandidateDTO::subjectId).collect(Collectors.toSet()));
        }
        return result.lines().map(String::trim).filter(l -> l.matches("\\d+"))
                .map(Long::parseLong).collect(Collectors.toSet());
    }

    // ── 内部方法 ──

    /**
     * 调用 LLM 从上下文生成最多 3 条搜索关键词。
     *
     * @param userInput 联合历史的上下文输入
     * @return 非空行列表；空时回退为输入原文
     */
    List<String> generateKeywords(String userInput) {
        String prompt = promptKeywords.replace("{today}", LocalDate.now().toString());
        String result = AiChatCallService.task()
                .node("pipeline-generateKeywords")
                .system(prompt)
                .user(userInput)
                .call();
        if (result == null || result.isBlank()) return List.of(userInput);
        return result.lines().map(String::trim).filter(l -> !l.isEmpty())
                .limit(Math.max(1, keywordLimit)).toList();
    }

    /**
     * 搜索引擎无结果时，让 LLM 选择公开影视榜单或资料 URL 并直接抓取。
     *
     * @param context 用户请求与历史上下文
     * @param keyword 当前失败的搜索关键词
     * @return 可用于标题提炼的页面文本
     */
    List<String> fetchDirectUrls(String context, String keyword) {
        String system = """
                你是影视资料检索助手。当前配置的 Web 搜索源没有可用结果。
                请根据用户需求和你的知识，选择最多 3 个公开 HTTP(S) URL，用于直接获取影视榜单、热门列表或资料页面。
                优先考虑 Bangumi、豆瓣、IMDb、AniList、Jikan/MyAnimeList、TMDb、公开视频平台榜单或公开 API。
                只输出 URL，每行一个；不要输出解释文字。
                今日日期: %s
                """.formatted(LocalDate.now());
        String user = "用户上下文：\n" + context + "\n\n失败关键词：\n" + keyword;
        String result = AiChatCallService.task()
                .node("pipeline-directFetchUrls")
                .system(system)
                .user(user)
                .call();
        if (result == null || result.isBlank()) return List.of();
        List<String> urls = result.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("http://") || line.startsWith("https://"))
                .distinct()
                .limit(Math.max(1, maxDirectUrls))
                .toList();
        if (urls.isEmpty()) return List.of();
        log.info("Pipeline: direct fetch fallback urls={}", urls);
        return urls.stream()
                .map(url -> webSearchTools.fetchWeb(url, "search-source-fallback",
                        directFetchMaxChars))
                .filter(resultItem -> resultItem != null && resultItem.ok())
                .map(resultItem -> resultItem.text())
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    /**
     * 从抓取文本中抽取候选作品标题。
     *
     * @param text 页面文本聚合
     * @param userInput 用户原始输入
     * @return 过滤后的标题列表
     */
    List<String> extractTitles(String text, String userInput) {
        int maxChars = Math.max(1, extractTextMaxChars);
        if (text.length() > maxChars) text = text.substring(0, maxChars);
        String prompt = promptTitles.replace("{today}", LocalDate.now().toString())
                .replace("{context}", userInput);
        String result = AiChatCallService.task()
                .node("pipeline-extractTitles")
                .system(prompt)
                .user(text)
                .call();
        if (result == null || result.isBlank()) return List.of();
        return result.lines().map(String::trim).filter(l -> !l.isEmpty() && l.length() < 100).toList();
    }

    /**
     * 生成“无结果”时的友好失败文案。
     *
     * @param userInput 用户原始输入
     * @param reason 失败原因（如关键词生成失败、关键词均无命中）
     * @return 向用户展示的中文说明
     */
    String failMessage(String userInput, String reason) {
        String prompt = promptFail.replace("{reason}", reason);
        String result = AiChatCallService.task()
                .node("pipeline-failMessage")
                .system(prompt)
                .user(userInput)
                .call();
        return result != null && !result.isBlank() ? result : "抱歉，未找到相关影视作品。";
    }
}

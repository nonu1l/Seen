package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.MatchedEntry;
import com.nonu1l.media.model.dto.WebSearchItem;
import com.nonu1l.media.service.BangumiTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多步搜索管道 Skill。
 *
 * 流程：
 * 1. 生成 3 组搜索关键词
 * 2. 依次搜索 → 多线程抓取 → LLM 提炼片名 → 多线程 Bangumi 匹配
 * 3. 够 3 条提前结束，全空则 LLM 生成失败原因
 */
public class SearchPipeline {

    private static final Logger log = LoggerFactory.getLogger(SearchPipeline.class);

    private final ChatClient chatClient;
    private final BangumiTools tools;
    private final static int maxCards = 5;
    private final static int maxPages = 10;
    private final String promptKeywords;
    private final String promptTitles;
    private final String promptValidate;
    private final String promptFail;

    public SearchPipeline(ChatClient chatClient, BangumiTools tools) {
        this.chatClient = chatClient;
        this.tools = tools;
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

    public record PipelineResult(List<MatchedEntry> cards, String failReason) {}

    // ── 主入口 ──

    public PipelineResult execute(String userInput) {
        return execute(userInput, "");
    }

    public PipelineResult execute(String userInput, String history) {
        String context = history.isEmpty() ? userInput : "对话历史：\n" + history + "\n当前请求：" + userInput;
        List<String> keywords = generateKeywords(context);
        if (keywords.isEmpty()) {
            return new PipelineResult(List.of(), failMessage(userInput, "关键词生成失败"));
        }

        List<MatchedEntry> allCards = new ArrayList<>();
        int tried = 0;

        for (String kw : keywords) {
            log.info("Pipeline: trying keyword '{}'", kw);
            tried++;

            // 2a. 搜索
            List<WebSearchItem> webResults = tools.searchWeb(kw);
            if (webResults.isEmpty()) continue;

            // 2b. 多线程抓取 + 清洗
            List<String> pageTexts = webResults.stream().limit(maxPages).parallel()
                    .map(r -> tools.fetchWeb(r.url()))
                    .filter(t -> t != null && !t.isBlank())
                    .toList();
            if (pageTexts.isEmpty()) continue;

            // 2c. LLM 提炼片名
            List<String> titles = extractTitles(String.join("\n---\n", pageTexts), context);
            if (titles.isEmpty()) continue;

            // 2d. 并发搜索→title→card映射，剔除空匹配和重复 subjectId
            // 使用原 .parallel() 对Map结果映射同时进行去重，过滤空值
            // 可能会与 LangGraph4j（共用ForkJoinPool.commonPool） 冲突引发 NullPointerException 问题

            var futures = titles.stream().distinct()
                    .collect(Collectors.toMap(
                            t -> t, t -> CompletableFuture.supplyAsync(() ->
                                    searchBangumiWithRetry(t, 3))));

            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join();
            var cardMap = new java.util.LinkedHashMap<String, MatchedEntry>();
            futures.forEach((t, f) -> { var c = f.join(); if (c != null) cardMap.put(t, c); });
            cardMap.values().removeIf(Objects::isNull);
            var seen = new HashSet<Long>();
            cardMap.values().removeIf(c -> !seen.add(c.subjectId()));

            // 校验匹配结果
            if (!cardMap.isEmpty()) {
                var validIds = validateMatchIds(cardMap, context);
                cardMap.values().removeIf(c -> !validIds.contains(c.subjectId()));
            }

            allCards.addAll(cardMap.values());
            log.info("Pipeline: keyword '{}' → {} cards", kw, cardMap.size());

            if (allCards.size() >= maxCards) break;
        }

        if (allCards.isEmpty()) {
            return new PipelineResult(List.of(),
                    failMessage(context, "已尝试 " + tried + " 组关键词，均未找到匹配的影视作品"));
        }
        return new PipelineResult(allCards, null);
    }

    MatchedEntry searchBangumiWithRetry(String keyword, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                var first = tools.searchBangumiOneResult(keyword);
                if (first != null) {
                    return new MatchedEntry(first.id(), first.nameCn(), null, null, null, null,
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

    /** 用 LLM 校验 title→card 映射，返回通过校验的 subjectId 集合 */
    java.util.Set<Long> validateMatchIds(Map<String, MatchedEntry> cardMap, String context) {
        if (cardMap.isEmpty()) return java.util.Set.of();
        StringBuilder sb = new StringBuilder();
        cardMap.forEach((title, c) -> sb.append(title).append(" → ").append(c.nameCn())
            .append(" (id=").append(c.subjectId())
            .append(c.airDate() != null ? ", 日期=" + c.airDate() : "")
            .append(")\n"));
        String prompt = promptValidate.replace("{today}", LocalDate.now().toString())
                .replace("{context}", context);
        String result = chatClient.prompt().system(prompt).user(sb.toString()).call().content();
        if (result == null || result.isBlank()) {
            return new java.util.HashSet<>(cardMap.values().stream().map(MatchedEntry::subjectId).collect(Collectors.toSet()));
        }
        return result.lines().map(String::trim).filter(l -> l.matches("\\d+"))
                .map(Long::parseLong).collect(Collectors.toSet());
    }

    // ── 内部方法 ──

    List<String> generateKeywords(String userInput) {
        String prompt = promptKeywords.replace("{today}", LocalDate.now().toString());
        String result = chatClient.prompt().system(prompt).user(userInput).call().content();
        if (result == null || result.isBlank()) return List.of(userInput);
        return result.lines().map(String::trim).filter(l -> !l.isEmpty()).limit(3).toList();
    }

    List<String> extractTitles(String text, String userInput) {
        if (text.length() > 8000) text = text.substring(0, 8000);
        String prompt = promptTitles.replace("{today}", LocalDate.now().toString())
                .replace("{context}", userInput);
        String result = chatClient.prompt().system(prompt).user(text).call().content();
        if (result == null || result.isBlank()) return List.of();
        return result.lines().map(String::trim).filter(l -> !l.isEmpty() && l.length() < 100).toList();
    }

    String failMessage(String userInput, String reason) {
        String prompt = promptFail.replace("{reason}", reason);
        String result = chatClient.prompt().system(prompt).user(userInput).call().content();
        return result != null && !result.isBlank() ? result : "抱歉，未找到相关影视作品。";
    }
}

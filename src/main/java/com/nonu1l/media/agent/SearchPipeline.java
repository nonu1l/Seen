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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

            // 2d. 多线程 Bangumi 匹配，取第一条（并发时 API 可能返回空，重试 3 次）
            // // 片名去重 → 匹配 → subjectId 去重
            List<MatchedEntry> cards = titles.stream()
                    .parallel()
                    .distinct()
                    .map(t -> searchBangumiWithRetry(t, 3))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(ArrayList::new));
            var seen = new java.util.HashSet<Long>();
            cards.removeIf(c -> !seen.add(c.subjectId()));

            // 校验匹配结果：LLM 判断 Bangumi 匹配是否与原片名一致（最多一次 LLM 调用）
            if (!cards.isEmpty()) {
                cards = validateMatches(cards, titles, context);
            }

            allCards.addAll(cards);
            log.info("Pipeline: keyword '{}' → {} cards", kw, cards.size());

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

    /** 用 LLM 校验 Bangumi 匹配结果与原片名是否为同一作品，过滤掉不匹配的 */
    List<MatchedEntry> validateMatches(List<MatchedEntry> cards, List<String> titles, String context) {
        if (cards.isEmpty() || titles.isEmpty()) return cards;
        StringBuilder pairs = new StringBuilder();
        var cardList = new ArrayList<>(cards);
        for (int i = 0; i < Math.min(cardList.size(), titles.size()); i++) {
            var c = cardList.get(i);
            pairs.append(titles.get(i)).append(" → ").append(c.nameCn())
                 .append(" (id=").append(c.subjectId())
                 .append(c.airDate() != null ? ", 日期=" + c.airDate() : "")
                 .append(")\n");
        }
        String prompt = promptValidate.replace("{today}", LocalDate.now().toString())
                .replace("{context}", context);
        String result = chatClient.prompt().system(prompt).user(pairs.toString()).call().content();
        if (result == null || result.isBlank()) return cards;
        var validIds = result.lines().map(String::trim).filter(l -> l.matches("\\d+"))
                .map(Long::parseLong).collect(java.util.stream.Collectors.toSet());
        return cardList.stream().filter(c -> validIds.contains(c.subjectId())).toList();
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

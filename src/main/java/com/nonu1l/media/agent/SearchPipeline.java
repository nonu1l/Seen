package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.MatchedEntry;
import com.nonu1l.media.model.dto.WebSearchItem;
import com.nonu1l.media.service.BangumiTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

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

    public SearchPipeline(ChatClient chatClient, BangumiTools tools) {
        this.chatClient = chatClient;
        this.tools = tools;
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
            List<MatchedEntry> cards = titles.stream()
                    .map(t -> searchBangumiWithRetry(t, 3))
                    .filter(Objects::nonNull)
                    .toList();

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
                var br = tools.searchBangumi(keyword);
                if (!br.isEmpty()) {
                    var first = br.getFirst();
                    return new MatchedEntry(first.id(), first.nameCn(), null, null, null, null);
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

    // ── 内部方法 ──

    List<String> generateKeywords(String userInput) {
        String prompt = ("今日日期: {today}\n根据用户查询生成3个搜索关键词（每行一个，只输出关键词，不要编号）")
                .replace("{today}", LocalDate.now().toString());
        String result = chatClient.prompt().system(prompt).user(userInput).call().content();
        if (result == null || result.isBlank()) return List.of(userInput);
        return result.lines().map(String::trim).filter(l -> !l.isEmpty()).limit(3).toList();
    }

    List<String> extractTitles(String text, String userInput) {
        if (text.length() > 8000) text = text.substring(0, 8000);
        String prompt = ("今日日期: {today}\n从以下网页内容中提取影视片名（每行一个，只输出片名，不要编号）。查询意图：" + userInput)
                .replace("{today}", LocalDate.now().toString());
        String result = chatClient.prompt().system(prompt).user(text).call().content();
        if (result == null || result.isBlank()) return List.of();
        return result.lines().map(String::trim).filter(l -> !l.isEmpty() && l.length() < 100).toList();
    }

    String failMessage(String userInput, String reason) {
        String prompt = "用户查询未找到匹配影视作品。原因：" + reason + "。请简短友好地告知用户并给出搜索建议。";
        String result = chatClient.prompt().system(prompt).user(userInput).call().content();
        return result != null && !result.isBlank() ? result : "抱歉，未找到相关影视作品。";
    }
}

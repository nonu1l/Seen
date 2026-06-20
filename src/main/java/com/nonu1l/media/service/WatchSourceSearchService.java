package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WatchSourceItemDTO;
import com.nonu1l.media.model.dto.WatchSourceResultDTO;
import com.nonu1l.media.model.dto.WebSearchItemDTO;
import com.nonu1l.media.model.dto.WebSearchResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在线观看地址搜索服务：从用户片源问题中抽取影视名称，并返回搜索到的候选观看链接。
 */
@Service
public class WatchSourceSearchService {

    private static final Logger log = LoggerFactory.getLogger(WatchSourceSearchService.class);
    private static final Pattern TITLE_PATTERN = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile("\"confidence\"\\s*:\\s*([0-9.]+)");
    private static final Pattern KEEP_PATTERN = Pattern.compile("\"keep\"\\s*:\\s*\\[([^]]*)]");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final int FETCH_CONTENT_MAX_CHARS = 3000;
    private static final int VALIDATION_CONTENT_MAX_CHARS = 2000;

    private final AiTextTaskService aiTextTaskService;
    private final WebSearchService webSearchService;
    private final WebFetchService webFetchService;
    private final String titlePrompt;
    private final String validatePrompt;

    /**
     * 创建片源搜索服务。
     *
     * @param aiTextTaskService 文本型 LLM 任务服务，用于校验候选页面内容
     * @param webSearchService Web 搜索服务，用于搜索候选观看链接
     * @param webFetchService Web 抓取服务，用于并发读取候选页面内容
     */
    public WatchSourceSearchService(AiTextTaskService aiTextTaskService,
                                    WebSearchService webSearchService,
                                    WebFetchService webFetchService) {
        this.aiTextTaskService = aiTextTaskService;
        this.webSearchService = webSearchService;
        this.webFetchService = webFetchService;
        this.titlePrompt = loadPrompt("prompts/watch-source-title.st");
        this.validatePrompt = loadPrompt("prompts/watch-source-validate.st");
    }

    /**
     * 搜索候选观看地址。
     *
     * <p>链路为：抽取影视名称、构造搜索词、并发打开候选 URL 获取页面内容，再由 LLM 根据页面内容筛选。
     * 不做 Bangumi 规范化、平台分类或正版排序。</p>
     *
     * @param query 用户原始片源问题或片名
     * @return 候选观看地址结果
     */
    public WatchSourceResultDTO search(String query) {
        if (!hasText(query)) {
            return failure(query, null, null, "query is blank", "请让用户提供想看的影视作品或描述。");
        }

        String rawQuery = query.trim();
        TitleGuess guess = resolveTitle(rawQuery);
        String title = guess.title();
        if (!hasText(title)) {
            return failure(rawQuery, null, null, "无法解析要搜索的作品名", "请让用户补充更明确的作品名。");
        }

        String searchQuery = buildSearchQuery(title);
        WebSearchResultDTO searchResult = webSearchService.searchWithDiagnostics(searchQuery);
        if (searchResult == null || !searchResult.ok()) {
            return failure(rawQuery, title, searchQuery,
                    searchResult != null ? searchResult.error() : "web search failed",
                    "当前没有搜索到可用观看地址；不要编造链接。");
        }

        List<WatchSourceItemDTO> items = fetchAndValidateItems(title, searchResult.items(), guess.confidence());
        if (items.isEmpty()) {
            return failure(rawQuery, title, searchQuery,
                    "搜索结果为空或候选页面无法访问", "可以告诉用户当前没有找到可靠观看地址。");
        }

        return new WatchSourceResultDTO(true, rawQuery, title, null, null,
                round(guess.confidence()), searchQuery, items, null, null);
    }

    /**
     * 用一次轻量 LLM 调用从用户问题中抽取影视名称。
     *
     * <p>提示词放在 prompts/watch-source-title.st；这里禁用思考模式，避免结构化抽取任务产生额外
        * reasoning 文本。</p>
     */
    private TitleGuess resolveTitle(String query) {
        try {
            String content = aiTextTaskService.task()
                    .node("watch-source-title")
                    .system(titlePrompt)
                    .user(query)
                    .thinking(AiThinkingMode.DISABLED)
                    .call();
            TitleGuess parsed = parseGuess(content);
            if (hasText(parsed.title())) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("watch source title resolve failed: {}", e.getMessage());
        }
        return new TitleGuess(query, 0.3d);
    }

    /**
     * 构造实际搜索词：影视名称 + 观看意图词。
     */
    private String buildSearchQuery(String title) {
        return title.trim() + " 在线观看 在哪看";
    }

    /**
     * 并发打开搜索结果 URL，汇聚可访问页面内容，再让 LLM 判断哪些候选更可能是目标作品的观看地址。
     */
    private List<WatchSourceItemDTO> fetchAndValidateItems(String title, List<WebSearchItemDTO> items, double confidence) {
        List<WatchSourceCandidate> candidates = toCandidates(items);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<FetchedWatchSource> fetched = fetchCandidates(candidates);
        if (fetched.isEmpty()) {
            return List.of();
        }

        List<Integer> keep = validateFetchedContent(title, fetched);
        if (keep.isEmpty()) {
            return List.of();
        }

        Map<Integer, FetchedWatchSource> byIndex = new LinkedHashMap<>();
        for (FetchedWatchSource item : fetched) {
            byIndex.put(item.index(), item);
        }
        return keep.stream()
                .map(byIndex::get)
                .filter(Objects::nonNull)
                .map(item -> toItem(item, confidence))
                .limit(8)
                .toList();
    }

    /**
     * 将 Web 搜索结果归一化为内部候选，按 URL 去重并限制后续 fetch 数量。
     */
    private List<WatchSourceCandidate> toCandidates(List<WebSearchItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, WatchSourceCandidate> deduped = new LinkedHashMap<>();
        for (WebSearchItemDTO item : items) {
            if (item == null || !hasText(item.url())) {
                continue;
            }
            String url = normalizeUrl(item.url());
            if (!hasText(url) || deduped.containsKey(url)) {
                continue;
            }
            deduped.put(url, new WatchSourceCandidate(item.title(), url, sourceHost(url)));
            if (deduped.size() >= 12) {
                break;
            }
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * 多线程访问候选 URL；无法访问、fetch 失败或没有正文内容的候选会被剔除。
     */
    private List<FetchedWatchSource> fetchCandidates(List<WatchSourceCandidate> candidates) {
        List<CompletableFuture<FetchedWatchSource>> futures = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            int index = i + 1;
            WatchSourceCandidate candidate = candidates.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> fetchCandidate(index, candidate)));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return futures.stream()
                .map(this::joinFetched)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 访问单个候选 URL 并提取打开后的页面标题与正文。
     */
    private FetchedWatchSource fetchCandidate(int index, WatchSourceCandidate candidate) {
        try {
            var fetched = webFetchService.fetch(candidate.url(), FETCH_CONTENT_MAX_CHARS);
            if (fetched == null || !fetched.ok() || !hasText(fetched.text())) {
                return null;
            }
            String pageTitle = firstText(fetched.title(), candidate.searchTitle(), candidate.source(), candidate.url());
            return new FetchedWatchSource(index, candidate.searchTitle(), pageTitle.trim(),
                    fetched.text().trim(), candidate.url(), candidate.source());
        } catch (Exception e) {
            log.debug("watch source candidate fetch failed url={} error={}", candidate.url(), e.getMessage());
            return null;
        }
    }

    /**
     * 读取并发 fetch 结果；单个任务失败不影响其他候选。
     */
    private FetchedWatchSource joinFetched(CompletableFuture<FetchedWatchSource> future) {
        try {
            return future.join();
        } catch (Exception e) {
            log.debug("watch source candidate future failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 调用 LLM 根据目标影视名称和实际打开后的页面内容筛选候选。
     */
    private List<Integer> validateFetchedContent(String title, List<FetchedWatchSource> fetched) {
        try {
            return aiTextTaskService.task()
                    .node("watch-source-validate")
                    .system(validatePrompt)
                    .user(validationInput(title, fetched))
                    .thinking(AiThinkingMode.DISABLED)
                    .maxAttempts(3)
                    .call(content -> {
                        Matcher keepMatcher = KEEP_PATTERN.matcher(content);
                        if (!keepMatcher.find()) {
                            return List.of();
                        }
                        return INTEGER_PATTERN.matcher(keepMatcher.group(1))
                                .results()
                                .map(match -> Integer.parseInt(match.group()))
                                .distinct()
                                .toList();
                    });
        } catch (Exception e) {
            log.warn("watch source content validate failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 组织给 LLM 的候选页面内容输入，正文会被压缩截断以控制单次验证 token。
     */
    private String validationInput(String title, List<FetchedWatchSource> fetched) {
        StringBuilder sb = new StringBuilder();
        sb.append("目标影视名称：").append(title).append('\n');
        sb.append("候选页面：\n");
        for (FetchedWatchSource item : fetched) {
            sb.append(item.index()).append(". ")
                    .append("页面标题：").append(item.pageTitle()).append('\n')
                    .append("   搜索标题：").append(firstText(item.searchTitle(), "")).append('\n')
                    .append("   URL：").append(item.url()).append('\n')
                    .append("   页面内容：").append(compactContent(item.pageContent(), VALIDATION_CONTENT_MAX_CHARS)).append('\n');
        }
        return sb.toString();
    }

    /**
     * 将 LLM 复核通过的候选转换成 Agent 可见的候选地址。
     */
    private WatchSourceItemDTO toItem(FetchedWatchSource item, double confidence) {
        return new WatchSourceItemDTO(
                item.pageTitle(),
                item.url(),
                item.source(),
                null,
                round(confidence),
                "已打开页面并根据页面内容筛选；实际是否可播放仍需用户打开确认。"
        );
    }

    /**
     * 从 LLM 输出中解析 title/confidence；非严格 JSON 也用正则容错。
     */
    private TitleGuess parseGuess(String content) {
        if (!hasText(content)) {
            return new TitleGuess(null, 0.0d);
        }
        Matcher titleMatcher = TITLE_PATTERN.matcher(content);
        String title = titleMatcher.find() ? titleMatcher.group(1).trim() : content.trim();
        Matcher confidenceMatcher = CONFIDENCE_PATTERN.matcher(content);
        double confidence = confidenceMatcher.find() ? parseDouble(confidenceMatcher.group(1), 0.5d) : 0.5d;
        return new TitleGuess(title, clamp(confidence));
    }

    /**
     * 统一构造失败返回，保留已解析出的标题和实际搜索词。
     */
    private WatchSourceResultDTO failure(String query, String title, String searchQuery, String error, String hint) {
        return new WatchSourceResultDTO(false, query, title, null, null, 0.0d,
                searchQuery, List.of(), error, hint);
    }

    /**
     * 归一化并校验候选 URL，只允许带 host 的 HTTP(S) 链接返回给 Agent。
     */
    private String normalizeUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            return uri.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从 URL 中提取来源域名，并去掉常见的 www. 前缀。
     */
    private String sourceHost(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 读取 classpath 下的 prompt 模板。
     */
    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load prompt: " + path, e);
        }
    }

    /**
     * 安全解析 double，失败时返回调用方提供的兜底值。
     */
    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    /**
     * 将置信度限制在 0 到 1 之间。
     */
    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /**
     * 将置信度保留两位小数，避免工具结果中出现过长浮点数。
     */
    private static double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    /**
     * 返回第一个非空白字符串，用于多来源字段兜底。
     */
    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 压缩页面正文中的连续空白并限制长度，避免候选内容过长影响验证调用。
     */
    private static String compactContent(String value, int maxChars) {
        if (!hasText(value)) {
            return "";
        }
        String compacted = value.replaceAll("\\s+", " ").trim();
        if (compacted.length() <= maxChars) {
            return compacted;
        }
        return compacted.substring(0, maxChars) + "...";
    }

    /**
     * 判断字符串是否存在非空白内容。
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 标题抽取结果，confidence 表示抽取出的片名可信度。
     */
    private record TitleGuess(String title, double confidence) {
    }

    /**
     * 搜索阶段的候选链接。
     */
    private record WatchSourceCandidate(String searchTitle, String url, String source) {
    }

    /**
     * 已成功打开并提取到页面内容的候选链接。
     */
    private record FetchedWatchSource(int index, String searchTitle, String pageTitle, String pageContent,
                                      String url, String source) {
    }
}

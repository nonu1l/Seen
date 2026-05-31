package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.CompactResult;
import com.nonu1l.media.model.dto.MatchedEntry;
import com.nonu1l.media.model.dto.WebSearchItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.service.BangumiTools;
import com.nonu1l.media.service.IntentAnalysisService;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Agent Graph Service — LangGraph4j 构建确定性的多步 Agent 流程。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final ChatClient chatClient;
    private final BangumiTools tools;
    private final ObjectMapper objectMapper;
    private final String classifyPrompt;
    private final SearchPipeline searchPipeline;

    public AgentService(ChatClient.Builder chatClientBuilder, BangumiTools tools,
                         ObjectMapper objectMapper, TokenUsageAdvisor tokenAdvisor) {
        this.chatClient = chatClientBuilder.defaultAdvisors(tokenAdvisor).build();
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.classifyPrompt = loadPrompt("prompts/classify-system.st");
        this.searchPipeline = new SearchPipeline(this.chatClient, tools);
    }

    public SeenAgentState invoke(String userInput, String history) throws GraphStateException {
        var graph = new StateGraph<>(SeenAgentState.SCHEMA, SeenAgentState::new)
            .addNode("classify",         node_async(this::classifyIntent))
            .addNode("mark",             node_async(this::handleMark))
            .addNode("unmark",           node_async(this::handleUnmark))
            .addNode("recommend",        node_async(this::handleRecommend))
            .addNode("search",           node_async(this::handleSearch))
            .addNode("analyze",          node_async(this::handleAnalyze))
            .addNode("output",           node_async(this::handleOutput))

            .addEdge(START, "classify")
            .addConditionalEdges("classify",
                edge_async(SeenAgentState::intent),
                Map.of("mark", "mark", "unmark", "unmark", "recommend", "recommend",
                       "search", "search", "analyze", "analyze"))
            .addEdge("mark", "output")
            .addEdge("unmark", "output")
            .addEdge("analyze", "output")
            .addEdge("recommend", "output")
            .addEdge("search", "output")
            .addEdge("output", END);

//        // 启动时打印 Mermaid 流程图（首次 invoke）
//        if (log.isInfoEnabled()) {
//            log.info("Agent Graph:\n{}", graph.getGraph(GraphRepresentation.Type.MERMAID, "Seen Agent").content());
//        }
        var compiled = graph.compile();

        try {
            var initial = Map.<String, Object>of("userInput", userInput, "history",
                    history != null ? history : "");
            return compiled.invoke(initial).orElseGet(() ->
                new SeenAgentState(Map.of("replyText", "抱歉，处理出错了，请重试。"))
            );
        } catch (Exception e) {
            log.error("Agent graph execution failed", e);
            return new SeenAgentState(Map.of("replyText", "抱歉，处理出错了，请重试。"));
        }
    }

    // ── 节点 ──

    Map<String, Object> classifyIntent(SeenAgentState s) {
        log.debug("Node[classify] enter: {}", s.userInput().length() > 100 ? s.userInput().substring(0, 100) + "..." : s.userInput());
        String system = classifyPrompt.replace("{today}", java.time.LocalDate.now().toString());
        String intent = chatClient.prompt().system(system).user(s.userInput()).call().content();
        if (intent != null) intent = intent.trim().toLowerCase();
        if (intent == null || !List.of("mark", "unmark", "recommend", "search", "analyze").contains(intent)) {
            intent = "analyze";
        }
        log.debug("Node[classify] exit: intent={}", intent);
        return Map.of("intent", intent);
    }

    Map<String, Object> handleMark(SeenAgentState s) {
        log.debug("Node[mark] enter");
        // 使用完整 agent prompt + 工具回调来处理标记类请求（提取片名 + 搜索匹配）
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        String content = chatClient.prompt()
                .system(system).user(s.userInput())
                .toolCallbacks(toolsAsCallbacks()).call().content();
        if (content != null && !content.isBlank()) {
            try {
                String json = IntentAnalysisService.extractJsonObject(content);
                var node = objectMapper.readTree(json);
                String reply = node.has("replyText") ? node.get("replyText").asText() : "已为你标记。";
                var cards = new ArrayList<MatchedEntry>();
                if (node.has("cards") && node.get("cards").isArray()) {
                    for (var c : node.get("cards")) {
                        cards.add(new MatchedEntry(
                            c.has("subjectId") ? c.get("subjectId").asLong() : null,
                            c.has("nameCn") ? c.get("nameCn").asText() : null,
                            c.has("rating") && !c.get("rating").isNull() ? c.get("rating").asInt() : null,
                            c.has("comment") && !c.get("comment").isNull() ? c.get("comment").asText() : null,
                            c.has("status") && !c.get("status").isNull() ? c.get("status").asText() : null,
                            null));
                    }
                }
                var unmarkIds = new ArrayList<Long>();
                if (node.has("unmarkIds") && node.get("unmarkIds").isArray()) {
                    for (var id : node.get("unmarkIds")) unmarkIds.add(id.asLong());
                }
                log.debug("Node[mark] exit: cards={} unmarkIds={}", cards.size(), unmarkIds.size());
                return Map.of("cards", cards, "replyText", reply, "unmarkIds", unmarkIds);
            } catch (Exception e) {
                log.warn("Mark JSON parse failed: {}", e.getMessage());
            }
        }
        log.debug("Node[mark] exit: failed");
        return Map.of("replyText", "未能识别你要标记的作品，请说清楚片名和状态。");
    }

    Map<String, Object> handleUnmark(SeenAgentState s) {
        log.debug("Node[unmark] enter");
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        String content = chatClient.prompt()
                .system(system).user(s.userInput())
                .toolCallbacks(toolsAsCallbacks()).call().content();
        if (content != null && !content.isBlank()) {
            try {
                String json = IntentAnalysisService.extractJsonObject(content);
                var node = objectMapper.readTree(json);
                String reply = node.has("replyText") ? node.get("replyText").asText() : "已处理。";
                var unmarkIds = new ArrayList<Long>();
                if (node.has("unmarkIds") && node.get("unmarkIds").isArray()) {
                    for (var id : node.get("unmarkIds")) {
                        unmarkIds.add(id.asLong());
                    }
                }
                log.debug("Node[unmark] exit: unmarkIds={}", unmarkIds.size());
                return Map.of("replyText", reply, "unmarkIds", unmarkIds);
            } catch (Exception e) {
                log.warn("Unmark JSON parse failed: {}", e.getMessage());
            }
        }
        return Map.of("replyText", "未能识别你要取消的作品，请说清楚片名。");
    }

    Map<String, Object> handleRecommend(SeenAgentState s) {
        log.debug("Node[recommend] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        var result = searchPipeline.execute(s.userInput(), s.history());
        log.debug("Node[recommend] exit: cards={}, fail={}", result.cards().size(), result.failReason());
        if (!result.cards().isEmpty()) {
            return Map.of("cards", (Object) result.cards());
        }
        return Map.of("replyText", result.failReason() != null ? result.failReason() : "抱歉，未找到相关作品。");
    }

    Map<String, Object> handleSearch(SeenAgentState s) {
        log.debug("Node[search] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        var result = searchPipeline.execute(s.userInput(), s.history());
        log.debug("Node[search] exit: cards={}, fail={}", result.cards().size(), result.failReason());
        if (!result.cards().isEmpty()) {
            return Map.of("cards", (Object) result.cards());
        }
        return Map.of("replyText", result.failReason() != null ? result.failReason() : "抱歉，未找到相关作品。");
    }

    Map<String, Object> handleAnalyze(SeenAgentState s) {
        log.debug("Node[analyze] enter");
        String reply = chatClient.prompt()
            .system("你是影视助手。根据用户问题简要回答。").user(s.userInput()).call().content();
        log.debug("Node[analyze] exit: reply={}", reply != null ? reply.substring(0, Math.min(100, reply.length())) : "null");
        return Map.of("replyText", reply != null ? reply : "抱歉，无法回答。");
    }

    Map<String, Object> handleOutput(SeenAgentState s) {
        log.debug("Node[output] enter: intent={}", s.intent());
        // 如果前面节点已经生成了 replyText（如 pipeline 失败），直接透传
        String preReply = s.replyText();
        if (preReply != null && !preReply.isBlank()) {
            log.debug("Node[output] passthrough replyText");
            return Map.of();
        }
        // 如果已经有 cards（如 pipeline 成功），只生成回复文案
        var existingCards = s.<MatchedEntry>cards();
        if (!existingCards.isEmpty()) {
            StringBuilder cardInfo = new StringBuilder();
            for (int i = 0; i < existingCards.size(); i++) {
                var c = existingCards.get(i);
                cardInfo.append(String.format("[%d] %s (subjectId=%d)\n", i + 1, c.nameCn(), c.subjectId()));
            }
            String reply = chatClient.prompt()
                .system("你是影视助手。根据以下卡片列表生成推荐回复文案，语气友好简洁。")
                .user(s.userInput() + "\n\n卡片列表：\n" + cardInfo)
                .call().content();
            log.debug("Node[output] generated reply for {} cards", existingCards.size());
            return Map.of("replyText", reply != null ? reply : "推荐如下：");
        }
        // 兜底：旧 LLM 全工具流程
        log.debug("Node[output] fallback to full tool call");
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        String content = chatClient.prompt()
                .system(system)
                .user(s.userInput())
                .toolCallbacks(toolsAsCallbacks())
                .call()
                .content();
        if (content != null && !content.isBlank()) {
            // 尝试提取 JSON，失败则当纯文本回复
            try {
                String json = IntentAnalysisService.extractJsonObject(content);
                var node = objectMapper.readTree(json);
                String reply = node.has("replyText") ? node.get("replyText").asText() : content;
                var cardList = new ArrayList<com.nonu1l.media.model.dto.MatchedEntry>();
                if (node.has("cards") && node.get("cards").isArray()) {
                    for (var c : node.get("cards")) {
                        cardList.add(new com.nonu1l.media.model.dto.MatchedEntry(
                            c.has("subjectId") ? c.get("subjectId").asLong() : null,
                            c.has("nameCn") ? c.get("nameCn").asText() : null,
                            c.has("rating") && !c.get("rating").isNull() ? c.get("rating").asInt() : null,
                            c.has("comment") && !c.get("comment").isNull() ? c.get("comment").asText() : null,
                            c.has("status") && !c.get("status").isNull() ? c.get("status").asText() : null,
                            null));
                    }
                }
                return Map.of("replyText", reply, "cards", cardList);
            } catch (Exception e) {
                return Map.of("replyText", content);
            }
        }
        return Map.of("replyText", "抱歉，无法处理你的请求。");
    }

    // ── helpers ─────────────────────────────────────────────


    private ToolCallback[] toolsAsCallbacks() {
        return new ToolCallback[] {
            FunctionToolCallback.builder("searchBangumi",
                    (SearchReq req) -> tools.searchBangumi(req.keyword()))
                .description("搜索 Bangumi 影视数据库")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("searchLocal",
                    (SearchReq req) -> tools.searchLocal(req.keyword()))
                .description("查询本地已标记的作品记录")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("searchWeb",
                    (SearchReq req) -> tools.searchWeb(req.keyword()))
                .description("搜索引擎")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("fetchWeb",
                    (FetchReq req) -> tools.fetchWeb(req.url()))
                .description("抓取网页纯文本")
                .inputType(FetchReq.class).build(),
        };
    }

    record SearchReq(String keyword) {}
    record FetchReq(String url) {}

    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "判断用户意图，只输出一个单词：mark/recommend/search/analyze";
        }
    }
}

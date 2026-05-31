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

    public AgentService(ChatClient.Builder chatClientBuilder, BangumiTools tools,
                         ObjectMapper objectMapper, TokenUsageAdvisor tokenAdvisor) {
        this.chatClient = chatClientBuilder.defaultAdvisors(tokenAdvisor).build();
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.classifyPrompt = loadPrompt("prompts/classify-system.st");
    }

    public SeenAgentState invoke(String userInput, String history) throws GraphStateException {
        var graph = new StateGraph<>(SeenAgentState.SCHEMA, SeenAgentState::new)
            .addNode("classify",         node_async(this::classifyIntent))
            .addNode("mark",             node_async(this::handleMark))
            .addNode("recommend",        node_async(this::handleRecommend))
            .addNode("search",           node_async(this::handleSearch))
            .addNode("analyze",          node_async(this::handleAnalyze))
            .addNode("retrySearch",      node_async(this::retrySearch))
            .addNode("output",           node_async(this::handleOutput))

            .addEdge(START, "classify")
            .addConditionalEdges("classify",
                edge_async(SeenAgentState::intent),
                Map.of("mark", "mark", "recommend", "recommend",
                       "search", "search", "analyze", "analyze"))
            .addEdge("mark", "output")
            .addEdge("analyze", "output")
            .addEdge("search", "output")
            .addConditionalEdges("recommend",
                edge_async(s -> s.retryCount() < 1 && s.searchResults().isEmpty() ? "retrySearch" : "output"),
                Map.of("retrySearch", "retrySearch", "output", "output"))
            .addEdge("retrySearch", "output")
            .addEdge("output", END);

        // 启动时打印 Mermaid 流程图（首次 invoke）
        if (log.isInfoEnabled()) {
            log.info("Agent Graph:\n{}", graph.getGraph(GraphRepresentation.Type.MERMAID, "Seen Agent").content());
        }
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
        if (intent == null || !List.of("mark", "recommend", "search", "analyze").contains(intent)) {
            intent = "analyze";
        }
        log.debug("Node[classify] exit: intent={}", intent);
        return Map.of("intent", intent);
    }

    Map<String, Object> handleMark(SeenAgentState s) {
        log.debug("Node[mark] enter");
        var results = tools.searchBangumi(s.userInput());
        var cards = new ArrayList<MatchedEntry>();
        if (!results.isEmpty()) {
            var r = results.get(0);
            cards.add(new MatchedEntry(r.id(), r.nameCn(), null, null, null, null));
        }
        log.debug("Node[mark] exit: cards={}", cards.size());
        return Map.of("cards", cards, "replyText", "已为你标记。");
    }

    Map<String, Object> handleRecommend(SeenAgentState s) {
        log.debug("Node[recommend] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        var results = tools.searchWeb(s.userInput());
        log.debug("Node[recommend] exit: searchResults={}", results.size());
        return Map.of("searchResults", results);
    }

    Map<String, Object> handleSearch(SeenAgentState s) {
        log.debug("Node[search] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        var results = tools.searchWeb(s.userInput());
        log.debug("Node[search] exit: searchResults={}", results.size());
        return Map.of("searchResults", results);
    }

    Map<String, Object> handleAnalyze(SeenAgentState s) {
        log.debug("Node[analyze] enter");
        String reply = chatClient.prompt()
            .system("你是影视助手。根据用户问题简要回答。").user(s.userInput()).call().content();
        log.debug("Node[analyze] exit: reply={}", reply != null ? reply.substring(0, Math.min(100, reply.length())) : "null");
        return Map.of("replyText", reply != null ? reply : "抱歉，无法回答。");
    }

    Map<String, Object> retrySearch(SeenAgentState s) {
        log.debug("Node[retrySearch] enter (retry {})", s.retryCount());
        String altQuery = s.userInput().replaceAll("[，！。？]", " ").trim();
        if (altQuery.length() < 3) altQuery = s.userInput() + " 推荐";
        var results = tools.searchWeb(altQuery);
        log.debug("Node[retrySearch] exit: searchResults={}", results.size());
        return Map.of("searchResults", results, "retryCount", s.retryCount() + 1);
    }

    Map<String, Object> handleOutput(SeenAgentState s) {
        log.debug("Node[output] enter: intent={}", s.intent());
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

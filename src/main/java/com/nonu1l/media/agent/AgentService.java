package com.nonu1l.media.agent;

import com.nonu1l.media.model.dto.MatchedEntry;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.agent.tool.AiBangumiTools;
import com.nonu1l.media.agent.tool.AiToolRegistry;
import com.nonu1l.media.agent.tool.AiWebSearchTools;
import com.nonu1l.media.service.AiChatClientFactory;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import com.nonu1l.media.service.IntentAnalysisService;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 基于 LangGraph4j 的对话 Agent 服务：先做意图分类，再路由到标记/取消/推荐/搜索/分析节点，最终输出回复与卡片建议。
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AiChatClientFactory chatClientFactory;
    private final AiToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final String classifyPrompt;
    private final SearchPipeline searchPipeline;
    private final AiPreferenceMemoryService memoryService;

    /**
     * @param chatClientFactory 运行时 AI 客户端工厂
     * @param toolRegistry AI 工具回调注册器
     * @param bangumiTools AI Bangumi 查询工具
     * @param webSearchTools AI Web 搜索工具
     * @param memoryService AI 长期偏好记忆服务
     * @param objectMapper JSON 解析器
     */
    public AgentService(AiChatClientFactory chatClientFactory,
                         AiToolRegistry toolRegistry,
                         AiBangumiTools bangumiTools,
                         AiWebSearchTools webSearchTools,
                         AiPreferenceMemoryService memoryService,
                         ObjectMapper objectMapper) {
        this.chatClientFactory = chatClientFactory;
        this.toolRegistry = toolRegistry;
        this.memoryService = memoryService;
        this.objectMapper = objectMapper;
        this.classifyPrompt = loadPrompt("prompts/classify-system.st");
        this.searchPipeline = new SearchPipeline(this::chatClient, bangumiTools, webSearchTools);
    }

    /**
     * 按 LangGraph4j 图执行一次 Agent 对话。
     *
     * @param userInput 用户输入
     * @param history 会话历史文本（用于消解“上条/第一个”等指代）
     * @return 解析后的状态对象，包含 intent、replyText、cards、unmarkIds
     * @throws GraphStateException 图执行异常
     */
    public SeenAgentState invoke(String userInput, String history) throws GraphStateException {
        return invoke(userInput, history, AgentRunEvents.noop());
    }

    /**
     * 按 LangGraph4j 图执行一次 Agent 对话，并将运行状态发送给监听器。
     *
     * @param userInput 用户输入
     * @param history 会话历史文本（用于消解“上条/第一个”等指代）
     * @param listener 单轮运行监听器
     * @return 解析后的状态对象，包含 intent、replyText、cards、unmarkIds
     * @throws GraphStateException 图执行异常
     */
    public SeenAgentState invoke(String userInput, String history, AgentRunListener listener) throws GraphStateException {
        AgentRunListener runListener = listener != null ? listener : AgentRunEvents.noop();
        var graph = new StateGraph<>(SeenAgentState.SCHEMA, SeenAgentState::new)
            .addNode("classify",         node_async(s -> classifyIntent(s, runListener)))
            .addNode("mark",             node_async(s -> handleMark(s, runListener)))
            .addNode("unmark",           node_async(s -> handleUnmark(s, runListener)))
            .addNode("recommend",        node_async(s -> handleRecommend(s, runListener)))
            .addNode("search",           node_async(s -> handleSearch(s, runListener)))
            .addNode("analyze",          node_async(s -> handleAnalyze(s, runListener)))
            .addNode("output",           node_async(s -> handleOutput(s, runListener)))

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

    /**
     * 分类节点：调用 LLM 判定意图并返回下一跳边。
     *
     * @param s 当前状态
     * @return intent 键值 map，值仅限 mark/unmark/recommend/search/analyze
     */
    Map<String, Object> classifyIntent(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[classify] enter: {}", s.userInput().length() > 100 ? s.userInput().substring(0, 100) + "..." : s.userInput());
        listener.status("正在理解需求");
        String system = classifyPrompt.replace("{today}", java.time.LocalDate.now().toString());
        TokenUsageAdvisor.setCurrentNode("classify");
        String intent = chatClient().prompt().system(system).user(s.userInput()).call().content();
        if (intent != null) intent = intent.trim().toLowerCase();
        if (intent == null || !List.of("mark", "unmark", "recommend", "search", "analyze").contains(intent)) {
            intent = "analyze";
        }
        log.debug("Node[classify] exit: intent={}", intent);
        return Map.of("intent", intent);
    }

    /**
     * 标记节点：通过完整系统提示与工具回调，解析用户标记意图为卡片/取消标记列表。
     *
     * @param s 当前状态
     * @return 包含 replyText、cards、unmarkIds 的 map；解析失败则返回提示文案
     */
    Map<String, Object> handleMark(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[mark] enter");
        listener.status("正在解析标记请求");
        TokenUsageAdvisor.setCurrentNode("mark");
        // 使用完整 agent prompt + 工具回调来处理标记类请求（提取片名 + 搜索匹配）
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        listener.status("正在查询作品信息");
        String content = chatClient().prompt()
                .system(system).user(s.userInput())
                .toolCallbacks(toolRegistry.callbacks()).call().content();
        listener.status("正在整理标记结果");
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

    /**
     * 取消标记节点：解析待取消作品，并返回 subjectId 列表。
     *
     * @param s 当前状态
     * @return 包含 replyText 与 unmarkIds 的 map；解析失败返回兜底提示文案
     */
    Map<String, Object> handleUnmark(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[unmark] enter");
        listener.status("正在解析取消标记请求");
        TokenUsageAdvisor.setCurrentNode("unmark");
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        listener.status("正在查询本地记录");
        String content = chatClient().prompt()
                .system(system).user(s.userInput())
                .toolCallbacks(toolRegistry.callbacks()).call().content();
        listener.status("正在整理取消标记结果");
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

    /**
     * 推荐节点：运行搜索流水线并返回卡片建议。
     *
     * @param s 当前状态
     * @return 有结果返回 cards；无结果返回 failReason 或默认提示
     */
    Map<String, Object> handleRecommend(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[recommend] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        listener.status("正在寻找适合的作品");
        TokenUsageAdvisor.setCurrentNode("recommend");
        var result = searchPipeline.execute(withMemoryContext(s.userInput(), memoryService.getMemoryContext()), s.history(), listener);
        log.debug("Node[recommend] exit: cards={}, fail={}", result.cards().size(), result.failReason());
        if (!result.cards().isEmpty()) {
            return Map.of("cards", (Object) result.cards());
        }
        return Map.of("replyText", result.failReason() != null ? result.failReason() : "抱歉，未找到相关作品。");
    }

    /**
     * 搜索节点：同推荐节点，区别在于语义上给用户明确执行“搜索”入口，流程复用搜索流水线。
     *
     * @param s 当前状态
     * @return 有结果返回 cards；否则返回 failReason 或默认提示
     */
    Map<String, Object> handleSearch(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[search] enter: {}", s.userInput().length() > 80 ? s.userInput().substring(0, 80) : s.userInput());
        listener.status("正在搜索作品");
        TokenUsageAdvisor.setCurrentNode("search");
        var result = searchPipeline.execute(withMemoryContext(s.userInput(), memoryService.getBriefMemoryContext()), s.history(), listener);
        log.debug("Node[search] exit: cards={}, fail={}", result.cards().size(), result.failReason());
        if (!result.cards().isEmpty()) {
            return Map.of("cards", (Object) result.cards());
        }
        return Map.of("replyText", result.failReason() != null ? result.failReason() : "抱歉，未找到相关作品。");
    }

    /**
     * 分析节点：基于历史信息生成自然语言回答，不进行推荐/标记的结构化返回。
     *
     * @param s 当前状态
     * @return 仅含 replyText 的 map
     */
    Map<String, Object> handleAnalyze(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[analyze] enter");
        listener.status("正在分析你的记录");
        TokenUsageAdvisor.setCurrentNode("analyze");
        String system = loadPrompt("prompts/agent-analyze.st")
                .replace("{history}", s.history())
                .replace("{memoryContext}", memoryService.getMemoryContext());
        String reply = generateReply(system, s.userInput(), listener);
        log.debug("Node[analyze] exit: reply={}", reply != null ? reply.substring(0, Math.min(100, reply.length())) : "null");
        return Map.of("replyText", reply != null ? reply : "抱歉，无法回答。");
    }

    /**
     * 输出节点：按上游状态合成最终回复文本，必要时附带卡片列表或 JSON 兼容回退。
     *
     * @param s 当前状态
     * @return 包含 replyText，必要时包含 cards 的 map
     */
    Map<String, Object> handleOutput(SeenAgentState s, AgentRunListener listener) {
        log.debug("Node[output] enter: intent={}", s.intent());
        TokenUsageAdvisor.setCurrentNode("output");
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
                cardInfo.append(String.format("[%d] %s\n", i + 1, c.nameCn()));
            }
            String reply = generateReply(loadPrompt("prompts/agent-output-reply.st"),
                    s.userInput() + "\n\n卡片列表：\n" + cardInfo, listener);
            log.debug("Node[output] generated reply for {} cards", existingCards.size());
            return Map.of("replyText", reply != null ? reply : "推荐如下：");
        }
        // 兜底：旧 LLM 全工具流程
        log.debug("Node[output] fallback to full tool call");
        String system = loadPrompt("prompts/agent-system.st")
                .replace("{today}", java.time.LocalDate.now().toString())
                .replace("{history}", s.history());
        String content = chatClient().prompt()
                .system(system)
                .user(s.userInput())
                .toolCallbacks(toolRegistry.callbacks())
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

    /**
     * 生成最终面向用户的自然语言回复；流式监听器会逐段接收 delta。
     *
     * @param system system prompt
     * @param user user prompt
     * @param listener 单轮运行监听器
     * @return 聚合后的完整回复文本
     */
    private String generateReply(String system, String user, AgentRunListener listener) {
        listener.status("正在生成回复");
        if (!listener.streamDeltas()) {
            return chatClient().prompt().system(system).user(user).call().content();
        }

        StringBuilder reply = new StringBuilder();
        try {
            chatClient().prompt()
                    .system(system)
                    .user(user)
                    .stream()
                    .content()
                    .doOnNext(delta -> {
                        reply.append(delta);
                        listener.delta(delta);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.warn("Stream reply failed, falling back to call: {}", e.getMessage());
            String fallback = chatClient().prompt().system(system).user(user).call().content();
            if (fallback != null && reply.isEmpty()) {
                listener.delta(fallback);
            }
            return fallback;
        }
        return reply.toString();
    }

    private ChatClient chatClient() {
        return chatClientFactory.currentClient();
    }

    /**
     * 将长期偏好画像拼到当前请求前，并明确当前请求拥有最高优先级。
     *
     * @param userInput 用户原始输入
     * @param memoryContext 长期偏好画像上下文
     * @return 供搜索流水线理解的增强输入
     */
    private String withMemoryContext(String userInput, String memoryContext) {
        if (memoryContext == null || memoryContext.isBlank()) {
            return userInput;
        }
        return "长期偏好画像（仅作为辅助，不得覆盖当前请求）：\n"
                + memoryContext
                + "\n\n当前请求（最高优先级）：\n"
                + userInput;
    }

    /**
     * 从 classpath 读取提示词模板。
     *
     * @param path 资源路径
     * @return 提示词内容；读取失败返回兜底提示词
     */
    private String loadPrompt(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "判断用户意图，只输出一个单词：mark/recommend/search/analyze";
        }
    }
}

package com.nonu1l.media.agent;

import com.nonu1l.media.agent.tool.AgentToolRegistry;
import com.nonu1l.media.agent.tool.AiAutonomousTools;
import com.nonu1l.media.agent.tool.AiLocalLibraryTools;
import com.nonu1l.media.agent.tool.AiWebSearchTools;
import com.nonu1l.media.service.AiChatCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 分析/问答 Runner：只暴露只读工具；搜索源关闭时不回答实时事实。
 */
@Component
public class AnalysisAgentRunner extends AbstractAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisAgentRunner.class);

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern REALTIME_PATTERN = Pattern.compile(
            "(最新|最近|当前|现在|今日|今天|今年|榜单|排行|票房|热映|上映|实时)");

    private final AgentToolRegistry toolRegistry;
    private final AiLocalLibraryTools localLibraryTools;
    private final AiAutonomousTools autonomousTools;
    private final AiWebSearchTools webSearchTools;
    private final ObjectMapper objectMapper;
    private final int maxSteps;
    private final int maxToolCalls;
    private final int maxObservationChars;
    private final int maxScratchpadChars;
    private final String stepPrompt;
    private final String finalPrompt;

    /**
     * @param aiChatCallService LLM 调用服务
     * @param contentBlockService 内容块转换服务
     * @param toolRegistry 工具注册器
     * @param localLibraryTools 本地片库只读工具
     * @param autonomousTools 包含 getWorkState/readUserMemory 的工具 Bean
     * @param webSearchTools Web 搜索与抓取工具
     * @param objectMapper JSON 映射工具
     * @param maxSteps ReAct 最大分析步数
     * @param maxToolCalls ReAct 本轮最多工具调用次数
     * @param maxObservationChars 单条工具观察最大字符数
     * @param maxScratchpadChars 累计观察 scratchpad 最大字符数
     */
    public AnalysisAgentRunner(AiChatCallService aiChatCallService,
                               AnthropicContentBlockService contentBlockService,
                               AgentToolRegistry toolRegistry,
                               AiLocalLibraryTools localLibraryTools,
                               AiAutonomousTools autonomousTools,
                               AiWebSearchTools webSearchTools,
                               ObjectMapper objectMapper,
                               @Value("${app.runtime.agent.analysis-react.max-steps:4}") int maxSteps,
                               @Value("${app.runtime.agent.analysis-react.max-tool-calls:4}") int maxToolCalls,
                               @Value("${app.runtime.agent.analysis-react.max-observation-chars:2000}") int maxObservationChars,
                               @Value("${app.runtime.agent.analysis-react.max-scratchpad-chars:6000}") int maxScratchpadChars) {
        super(aiChatCallService, contentBlockService, "prompts/agent-analysis.st");
        this.toolRegistry = toolRegistry;
        this.localLibraryTools = localLibraryTools;
        this.autonomousTools = autonomousTools;
        this.webSearchTools = webSearchTools;
        this.objectMapper = objectMapper;
        this.maxSteps = Math.max(1, maxSteps);
        this.maxToolCalls = Math.max(1, maxToolCalls);
        this.maxObservationChars = Math.max(1, maxObservationChars);
        this.maxScratchpadChars = Math.max(1, maxScratchpadChars);
        this.stepPrompt = AgentPromptLoader.load("prompts/agent-analysis-react-step.st");
        this.finalPrompt = AgentPromptLoader.load("prompts/agent-analysis-react-final.st");
    }

    @Override
    public AgentCapability capability() {
        return AgentCapability.ANALYSIS;
    }

    @Override
    public AgentResponse run(String userInput, String history, AgentRunListener listener) {
        if (listener != null) {
            listener.status("正在分析");
        }
        boolean webEnabled = toolRegistry.isWebSearchEnabled();
        boolean hasUrl = userInput != null && URL_PATTERN.matcher(userInput).find();
        Set<String> allowedTools = AnalysisReActSupport.allowedToolNames(webEnabled, hasUrl);
        Map<String, ToolCallback> tools = toolMap(toolRegistry.selectLimited("agent-analysis-react", maxToolCalls,
                allowedTools,
                localLibraryTools, autonomousTools, webSearchTools));
        String realtimeGuidance = realtimeGuidance(userInput, webEnabled, hasUrl);
        List<AnalysisReActSupport.AnalysisReActTraceStep> traceSteps = new ArrayList<>();
        for (int step = 1; step <= maxSteps; step++) {
            if (listener != null) {
                listener.status("正在分析第 " + step + " 步");
            }
            AnalysisReActSupport.AnalysisReActDecision decision;
            try {
                decision = decideNextStep(userInput, history, realtimeGuidance, tools, traceSteps, step);
            } catch (RuntimeException e) {
                log.warn("Analysis ReAct decision failed at step {}: {}", step, e.getMessage());
                return finalAnswer(userInput, history, realtimeGuidance, traceSteps,
                        "结构化决策失败：" + e.getMessage());
            }
            if (decision.isFinal()) {
                return response(decision.answer(), traceSteps);
            }
            AnalysisReActSupport.AnalysisReActTraceStep traceStep = callTool(decision, tools, listener);
            traceSteps.add(traceStep);
        }
        return finalAnswer(userInput, history, realtimeGuidance, traceSteps, "达到最大分析步数");
    }

    private String realtimeGuidance(String userInput, boolean webEnabled, boolean hasUrl) {
        boolean realtime = userInput != null && REALTIME_PATTERN.matcher(userInput).find();
        if (!webEnabled && realtime && !hasUrl) {
            return "当前未启用搜索源，用户询问实时、最新或榜单类问题时，必须说明无法实时查询，不要凭旧知识回答。";
        }
        if (webEnabled) {
            return "搜索源已启用；实时、最新或榜单类问题可调用 searchWeb，失败后如实说明资料源不可用。";
        }
        return "当前未启用搜索源；只有用户提供明确 URL 时才可调用 fetchWeb 读取公开页面。";
    }

    /**
     * 调用文本模型产出下一步 ReAct 决策，并在解析失败时交给调用方收束。
     */
    private AnalysisReActSupport.AnalysisReActDecision decideNextStep(
            String userInput,
            String history,
            String realtimeGuidance,
            Map<String, ToolCallback> tools,
            List<AnalysisReActSupport.AnalysisReActTraceStep> traceSteps,
            int step) {
        Map<String, Object> params = baseParams(history);
        params.put("realtimeGuidance", realtimeGuidance);
        params.put("toolList", toolList(tools));
        params.put("scratchpad", AnalysisReActSupport.scratchpad(traceSteps, maxScratchpadChars));
        params.put("step", step);
        params.put("maxSteps", maxSteps);
        Set<String> allowedToolNames = new HashSet<>(tools.keySet());
        return aiChatCallService().task()
                .node("agent-analysis-react-step")
                .system(stepPrompt, params)
                .user(userInput)
                .maxAttempts(2)
                .call(content -> AnalysisReActSupport.parseDecision(content, objectMapper, allowedToolNames));
    }

    /**
     * 根据模型决策手动调用只读工具，并把结果压缩成下一轮可引用的观察。
     */
    private AnalysisReActSupport.AnalysisReActTraceStep callTool(
            AnalysisReActSupport.AnalysisReActDecision decision,
            Map<String, ToolCallback> tools,
            AgentRunListener listener) {
        ToolCallback callback = tools.get(decision.tool());
        if (callback == null) {
            return new AnalysisReActSupport.AnalysisReActTraceStep(decision.tool(), "{}",
                    "", "tool is unavailable");
        }
        if (listener != null) {
            listener.status(toolStatus(decision.tool()));
        }
        String inputJson = "{}";
        try {
            inputJson = objectMapper.writeValueAsString(decision.input());
            String result = callback.call(inputJson);
            return new AnalysisReActSupport.AnalysisReActTraceStep(
                    decision.tool(),
                    inputJson,
                    AnalysisReActSupport.limitText(result, maxObservationChars),
                    null
            );
        } catch (Exception e) {
            log.warn("Analysis ReAct tool '{}' failed: {}", decision.tool(), e.getMessage());
            return new AnalysisReActSupport.AnalysisReActTraceStep(
                    decision.tool(),
                    inputJson,
                    "",
                    AnalysisReActSupport.limitText(e.getMessage(), maxObservationChars)
            );
        }
    }

    /**
     * 在达到步数上限或决策失败时，基于已获得观察生成最终自然语言回答。
     */
    private AgentResponse finalAnswer(String userInput, String history, String realtimeGuidance,
                                      List<AnalysisReActSupport.AnalysisReActTraceStep> traceSteps,
                                      String finalReason) {
        Map<String, Object> params = baseParams(history);
        params.put("realtimeGuidance", realtimeGuidance);
        params.put("scratchpad", AnalysisReActSupport.scratchpad(traceSteps, maxScratchpadChars));
        params.put("finalReason", finalReason != null ? finalReason : "需要收束回答");
        String answer;
        try {
            answer = aiChatCallService().task()
                    .node("agent-analysis-react-final")
                    .system(finalPrompt, params)
                    .user(userInput)
                    .maxAttempts(2)
                    .call();
        } catch (RuntimeException e) {
            log.warn("Analysis ReAct finalizer failed: {}", e.getMessage());
            answer = "当前只读分析流程没有得到足够稳定的结果，请稍后重试或补充更明确的问题。";
        }
        if (answer == null || answer.isBlank()) {
            answer = "已根据现有资料完成分析。";
        }
        return response(answer, traceSteps);
    }

    /**
     * 组装最终回复和前端可展示的 ReAct 工具摘要内容块。
     */
    private AgentResponse response(String answer, List<AnalysisReActSupport.AnalysisReActTraceStep> traceSteps) {
        String content = answer != null && !answer.isBlank() ? answer.trim() : "已根据现有资料完成分析。";
        return new AgentResponse(content, AnalysisReActSupport.contentBlocksJson(objectMapper, content, traceSteps));
    }

    /**
     * 将注册器返回的工具回调按工具名索引，便于 ReAct 循环手动调用。
     */
    private Map<String, ToolCallback> toolMap(Object[] selectedTools) {
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        if (selectedTools == null) {
            return map;
        }
        for (Object tool : selectedTools) {
            if (tool instanceof ToolCallback callback) {
                map.put(callback.getToolDefinition().name(), callback);
            }
        }
        return map;
    }

    /**
     * 生成传入 ReAct 决策 prompt 的可用工具说明，包含 Spring AI 工具 schema。
     */
    private String toolList(Map<String, ToolCallback> tools) {
        if (tools == null || tools.isEmpty()) {
            return "无可用工具。";
        }
        return tools.values().stream()
                .map(callback -> callback.getToolDefinition().name()
                        + "："
                        + callback.getToolDefinition().description()
                        + "\ninputSchema: "
                        + callback.getToolDefinition().inputSchema())
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 将工具名映射为用户可见的 SSE 阶段状态。
     */
    private String toolStatus(String tool) {
        return switch (tool) {
            case "searchLocal" -> "正在查询本地片库";
            case "getWorkState" -> "正在查询作品状态";
            case "readUserMemory" -> "正在读取长期记忆";
            case "searchWeb" -> "正在搜索网页";
            case "fetchWeb" -> "正在读取网页";
            default -> "正在调用分析工具";
        };
    }
}

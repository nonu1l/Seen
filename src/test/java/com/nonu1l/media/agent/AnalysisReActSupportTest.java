package com.nonu1l.media.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Analysis ReAct 辅助逻辑的结构化解析与调试内容块测试。
 */
class AnalysisReActSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 合法 tool 决策应解析出工具名和参数对象。
     */
    @Test
    void parseToolDecision() {
        var decision = AnalysisReActSupport.parseDecision(
                "{\"type\":\"tool\",\"tool\":\"searchLocal\",\"input\":{\"keyword\":\"高分动画\"}}",
                objectMapper,
                Set.of("searchLocal")
        );

        assertFalse(decision.isFinal());
        assertEquals("searchLocal", decision.tool());
        assertEquals("高分动画", decision.input().get("keyword"));
    }

    /**
     * 合法 final 决策应直接返回最终回答。
     */
    @Test
    void parseFinalDecision() {
        var decision = AnalysisReActSupport.parseDecision(
                "{\"type\":\"final\",\"answer\":\"可以基于现有记录总结。\"}",
                objectMapper,
                Set.of("searchLocal")
        );

        assertTrue(decision.isFinal());
        assertEquals("可以基于现有记录总结。", decision.answer());
    }

    /**
     * 模型偶发输出 Markdown 代码块时仍应提取内部 JSON。
     */
    @Test
    void parseMarkdownWrappedJson() {
        var decision = AnalysisReActSupport.parseDecision(
                "```json\n{\"type\":\"final\",\"answer\":\"已完成。\"}\n```",
                objectMapper,
                Set.of("searchLocal")
        );

        assertTrue(decision.isFinal());
        assertEquals("已完成。", decision.answer());
    }

    /**
     * 越权工具和缺失字段应被拒绝，避免 ReAct 循环越过只读边界。
     */
    @Test
    void rejectInvalidDecision() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisReActSupport.parseDecision(
                "{\"type\":\"tool\",\"tool\":\"markWork\",\"input\":{\"subjectId\":1}}",
                objectMapper,
                Set.of("searchLocal")
        ));
        assertThrows(IllegalArgumentException.class, () -> AnalysisReActSupport.parseDecision(
                "{\"type\":\"tool\",\"tool\":\"searchLocal\"}",
                objectMapper,
                Set.of("searchLocal")
        ));
    }

    /**
     * 工具白名单应根据搜索源和 URL 条件暴露只读工具。
     */
    @Test
    void buildAllowedToolNames() {
        assertEquals(
                Set.of("searchLocal", "getWorkState", "readUserMemory", "searchWeb", "fetchWeb"),
                AnalysisReActSupport.allowedToolNames(true, false)
        );
        assertEquals(
                Set.of("searchLocal", "getWorkState", "readUserMemory", "fetchWeb"),
                AnalysisReActSupport.allowedToolNames(false, true)
        );
        assertEquals(
                Set.of("searchLocal", "getWorkState", "readUserMemory"),
                AnalysisReActSupport.allowedToolNames(false, false)
        );
    }

    /**
     * 单条 observation 和累计 scratchpad 都必须按配置截断。
     */
    @Test
    void limitObservationAndScratchpad() {
        String limited = AnalysisReActSupport.limitText("1234567890", 6);
        assertTrue(limited.length() <= 6);

        var steps = List.of(
                new AnalysisReActSupport.AnalysisReActTraceStep("searchLocal", "{\"keyword\":\"a\"}",
                        "A".repeat(80), null),
                new AnalysisReActSupport.AnalysisReActTraceStep("readUserMemory", "{\"query\":\"b\"}",
                        "B".repeat(80), null)
        );
        String scratchpad = AnalysisReActSupport.scratchpad(steps, 60);
        assertTrue(scratchpad.length() <= 60);
    }

    /**
     * content blocks 应至少包含最终 text，并可序列化工具调用摘要。
     */
    @Test
    void contentBlocksJsonContainsTextAndToolBlocks() throws Exception {
        var steps = List.of(new AnalysisReActSupport.AnalysisReActTraceStep(
                "searchLocal",
                "{\"keyword\":\"高分\"}",
                "{\"ok\":true}",
                null
        ));

        String json = AnalysisReActSupport.contentBlocksJson(objectMapper, "最终回答", steps);
        List<Map<String, Object>> blocks = objectMapper.readValue(json, List.class);

        assertEquals("text", blocks.get(0).get("type"));
        assertEquals("最终回答", blocks.get(0).get("text"));
        assertTrue(blocks.stream().anyMatch(block -> "tool_use".equals(block.get("type"))));
        assertTrue(blocks.stream().anyMatch(block -> "tool_result".equals(block.get("type"))));
    }
}

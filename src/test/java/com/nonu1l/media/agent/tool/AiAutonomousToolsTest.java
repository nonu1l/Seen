package com.nonu1l.media.agent.tool;

import com.nonu1l.media.agent.AgentRunEvents;
import com.nonu1l.media.repository.ConversationCardRepository;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import com.nonu1l.media.service.AiChatClientFactory;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import com.nonu1l.media.service.AiWorkOperationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 自主 Agent 作品动作工具的结构化失败返回测试。
 */
class AiAutonomousToolsTest {

    @AfterEach
    void clearToolContext() {
        AiToolContextHolder.clear();
    }

    @Test
    void unmarkWorkReturnsFailureWhenLocalRecordDoesNotExist() {
        AiWorkOperationService operationService = mock(AiWorkOperationService.class);
        AiToolSafetyService safetyService = mock(AiToolSafetyService.class);
        when(safetyService.checkUnmarkAllowed(any()))
                .thenReturn(AiToolSafetyService.SafetyDecision.allow());
        when(operationService.unmarkWork(100L, "用户要求取消")).thenReturn(null);
        setContext("取消男子高中生的日常标记");

        AiAutonomousTools tools = createTools(operationService, safetyService);

        var result = tools.unmarkWork(100L, "用户要求取消");

        assertFalse(result.ok());
        assertNull(result.card());
        assertEquals("本地没有可取消的记录", result.error());
    }

    @Test
    void unmarkWorkBlocksWholeLibraryDeleteRequest() {
        AiWorkOperationService operationService = mock(AiWorkOperationService.class);
        AiToolSafetyService safetyService = new AiToolSafetyService(mock(ConversationCardRepository.class));
        setContext("清空我的片库");
        AiAutonomousTools tools = createTools(operationService, safetyService);

        var result = tools.unmarkWork(100L, "用户要求取消");

        assertFalse(result.ok());
        assertEquals("拒绝执行整库级取消标记请求", result.error());
        verify(operationService, never()).unmarkWork(any(), any());
    }

    @Test
    void unmarkWorkBlocksWhenRequestLimitReached() {
        AiWorkOperationService operationService = mock(AiWorkOperationService.class);
        ConversationCardRepository cardRepo = mock(ConversationCardRepository.class);
        when(cardRepo.countBySessionIdAndRequestIdAndActionType(1L, "req-1", "UNMARK"))
                .thenReturn(5L);
        AiToolSafetyService safetyService = new AiToolSafetyService(cardRepo);
        setContext("取消这些候选的标记");
        AiAutonomousTools tools = createTools(operationService, safetyService);

        var result = tools.unmarkWork(100L, "用户要求取消");

        assertFalse(result.ok());
        assertEquals("本轮取消标记数量已达到安全上限 5 个", result.error());
        verify(operationService, never()).unmarkWork(any(), any());
    }

    @Test
    void safetyDoesNotTreatSeriesAllMoviesAsWholeLibraryRequest() {
        AiToolSafetyService safetyService = new AiToolSafetyService(mock(ConversationCardRepository.class));

        assertFalse(safetyService.isWholeLibraryUnmarkRequest("把复仇者联盟所有电影取消标记"));
    }

    @Test
    void safetyTreatsAllMoviesWithoutQualifierAsWholeLibraryRequest() {
        AiToolSafetyService safetyService = new AiToolSafetyService(mock(ConversationCardRepository.class));

        assertTrue(safetyService.isWholeLibraryUnmarkRequest("把所有电影都取消标记"));
    }

    private static AiAutonomousTools createTools(AiWorkOperationService operationService,
                                                 AiToolSafetyService safetyService) {
        return new AiAutonomousTools(
                mock(AiChatClientFactory.class),
                mock(AiBangumiTools.class),
                mock(AiWebSearchTools.class),
                mock(AiPreferenceMemoryService.class),
                operationService,
                mock(RecordRepository.class),
                mock(WorkRepository.class),
                safetyService
        );
    }

    private static void setContext(String userInput) {
        AiToolContextHolder.set(new AiToolExecutionContext(
                1L, "req-1", 10L, 11L, userInput, AgentRunEvents.noop()));
    }
}

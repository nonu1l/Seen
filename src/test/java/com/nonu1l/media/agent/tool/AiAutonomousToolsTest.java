package com.nonu1l.media.agent.tool;

import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import com.nonu1l.media.service.AiChatClientFactory;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import com.nonu1l.media.service.AiWorkOperationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 自主 Agent 作品动作工具的结构化失败返回测试。
 */
class AiAutonomousToolsTest {

    @Test
    void unmarkWorkReturnsFailureWhenLocalRecordDoesNotExist() {
        AiWorkOperationService operationService = mock(AiWorkOperationService.class);
        when(operationService.unmarkWork(100L, "用户要求取消")).thenReturn(null);

        AiAutonomousTools tools = new AiAutonomousTools(
                mock(AiChatClientFactory.class),
                mock(AiBangumiTools.class),
                mock(AiWebSearchTools.class),
                mock(AiPreferenceMemoryService.class),
                operationService,
                mock(RecordRepository.class),
                mock(WorkRepository.class)
        );

        var result = tools.unmarkWork(100L, "用户要求取消");

        assertFalse(result.ok());
        assertNull(result.card());
        assertEquals("本地没有可取消的记录", result.error());
    }
}

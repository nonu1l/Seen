package com.nonu1l.media.service;

import com.nonu1l.media.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据清理 — 每个方法清空指定表，附带级联清理依赖表避免外键冲突。
 * <p>注意：会删除真实数据，勿在生产环境运行。</p>
 */
@SpringBootTest
@Transactional
@Commit
public class DataCleanupTest {

    @Autowired private ConversationCardRepository cardRepo;
    @Autowired private ConversationMessageRepository messageRepo;
    @Autowired private ConversationSessionRepository sessionRepo;
    @Autowired private RecordRepository recordRepo;
    @Autowired private WorkRepository workRepo;
    @Autowired private RequestCacheRepository requestCacheRepo;

    @Test @DisplayName("清空 conversation_card")
    void clearConversationCard() { cardRepo.deleteAll(); }

    @Test @DisplayName("清空 conversation_message")
    void clearConversationMessage() { messageRepo.deleteAll(); }

    @Test @DisplayName("清空 conversation_session（先清 card + message）")
    void clearConversationSession() {
        cardRepo.deleteAll();
        messageRepo.deleteAll();
        sessionRepo.deleteAll();
    }

    @Test @DisplayName("清空 record")
    void clearRecord() { recordRepo.deleteAll(); }

    @Test @DisplayName("清空 work（先清 record）")
    void clearWork() {
        recordRepo.deleteAll();
        workRepo.deleteAll();
    }

    @Test @DisplayName("清空 request_cache")
    void clearRequestCache() { requestCacheRepo.deleteAll(); }

    @Test @DisplayName("清空全部 6 张表（按 FK 顺序）")
    void clearAll() {
        cardRepo.deleteAll();
        messageRepo.deleteAll();
        sessionRepo.deleteAll();
        recordRepo.deleteAll();
        workRepo.deleteAll();
        requestCacheRepo.deleteAll();
    }
}

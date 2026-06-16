package com.nonu1l.media.service;

import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.UserPreferenceEvidence;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.UserPreferenceEvidenceRepository;
import com.nonu1l.media.repository.UserPreferenceMemoryRepository;
import com.nonu1l.media.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 长期偏好记忆服务的纯聚合逻辑测试。
 */
class AiPreferenceMemoryServiceTest {

    @Test
    void getMemoryContextReturnsEmptyWhenMemoryDoesNotExist() {
        UserPreferenceMemoryRepository memoryRepo = mock(UserPreferenceMemoryRepository.class);
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.getBoolean(SettingsService.AI_MEMORY_ENABLED)).thenReturn(true);
        when(memoryRepo.findById(1L)).thenReturn(Optional.empty());

        AiPreferenceMemoryService service = newService(memoryRepo, mock(UserPreferenceEvidenceRepository.class),
                mock(WorkRepository.class), mock(RecordRepository.class), settingsService);

        assertEquals("", service.getMemoryContext());
    }

    @Test
    void buildPreferenceSnapshotCollectsHighLowReviewStatusAndRecentEvidence() {
        Work highWork = work(100L, "高分悬疑片");
        Work lowWork = work(200L, "弃坑异世界");
        Record highRecord = record(1L, 100L, "collect", 9.0d, "节奏紧凑，反转自然。");
        Record lowRecord = record(2L, 200L, "dropped", 4.0d, "套路重复，人物动机很弱。");

        WorkRepository workRepo = mock(WorkRepository.class);
        RecordRepository recordRepo = mock(RecordRepository.class);
        when(workRepo.findAllOrderByLatestRecord()).thenReturn(List.of(highWork, lowWork));
        when(recordRepo.findLatestByWorkIds(any(Set.class))).thenReturn(List.of(highRecord, lowRecord));
        when(recordRepo.findTop30ByOrderByUpdatedAtDescIdDesc()).thenReturn(List.of(highRecord, lowRecord));

        AiPreferenceMemoryService service = newService(mock(UserPreferenceMemoryRepository.class),
                mock(UserPreferenceEvidenceRepository.class), workRepo, recordRepo, mock(SettingsService.class));

        AiPreferenceMemoryService.PreferenceSnapshot snapshot = service.buildPreferenceSnapshot();
        List<String> types = snapshot.evidences().stream()
                .map(UserPreferenceEvidence::getEvidenceType)
                .toList();

        assertTrue(types.contains("high_rating"));
        assertTrue(types.contains("low_rating"));
        assertTrue(types.contains("review"));
        assertTrue(types.contains("status"));
        assertTrue(types.contains("recent"));
        assertTrue(snapshot.evidences().stream().anyMatch(e -> e.getText().contains("高分悬疑片")));
        assertTrue(snapshot.evidences().stream().anyMatch(e -> e.getText().contains("弃坑异世界")));
    }

    @Test
    void buildPreferenceSnapshotHashIsStableForSameEvidence() {
        Work work = work(100L, "稳定画像样本");
        Record record = record(1L, 100L, "collect", 9.0d, "喜欢克制的叙事。");

        WorkRepository workRepo = mock(WorkRepository.class);
        RecordRepository recordRepo = mock(RecordRepository.class);
        when(workRepo.findAllOrderByLatestRecord()).thenReturn(List.of(work));
        when(recordRepo.findLatestByWorkIds(any(Set.class))).thenReturn(List.of(record));
        when(recordRepo.findTop30ByOrderByUpdatedAtDescIdDesc()).thenReturn(List.of(record));

        AiPreferenceMemoryService service = newService(mock(UserPreferenceMemoryRepository.class),
                mock(UserPreferenceEvidenceRepository.class), workRepo, recordRepo, mock(SettingsService.class));

        String firstHash = service.buildPreferenceSnapshot().sourceHash();
        String secondHash = service.buildPreferenceSnapshot().sourceHash();

        assertEquals(firstHash, secondHash);
    }

    private AiPreferenceMemoryService newService(UserPreferenceMemoryRepository memoryRepo,
                                                 UserPreferenceEvidenceRepository evidenceRepo,
                                                 WorkRepository workRepo,
                                                 RecordRepository recordRepo,
                                                 SettingsService settingsService) {
        return new AiPreferenceMemoryService(memoryRepo, evidenceRepo, workRepo, recordRepo,
                mock(AiChatClientFactory.class), settingsService, mock(ObjectMapper.class));
    }

    private Work work(Long id, String nameCn) {
        Work work = new Work();
        work.setId(id);
        work.setNameCn(nameCn);
        work.setPlatform("TV");
        return work;
    }

    private Record record(Long id, Long workId, String status, Double rating, String review) {
        Record record = new Record();
        record.setId(id);
        record.setWorkId(workId);
        record.setStatus(status);
        record.setRating(rating);
        record.setReview(review);
        record.setUpdatedAt(Instant.parse("2026-06-17T00:00:00Z"));
        return record;
    }
}

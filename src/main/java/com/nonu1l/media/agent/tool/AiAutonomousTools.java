package com.nonu1l.media.agent.tool;

import com.nonu1l.media.agent.SearchPipeline;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.ConversationCardDTO;
import com.nonu1l.media.model.dto.LocalRecordDTO;
import com.nonu1l.media.model.dto.MatchedEntryDTO;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import com.nonu1l.media.service.AiChatClientFactory;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import com.nonu1l.media.service.AiWorkOperationService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自主 Agent 专用工具集合，封装会话上下文感知的搜索、展示和写库操作。
 */
@Component
public class AiAutonomousTools {

    private final AiLocalLibraryTools localLibraryTools;
    private final AiPreferenceMemoryService memoryService;
    private final AiWorkOperationService operationService;
    private final RecordRepository recordRepo;
    private final WorkRepository workRepo;
    private final SearchPipeline searchPipeline;

    /**
     * 创建自主 Agent 工具集合。
     *
     * @param chatClientFactory AI 客户端工厂
     * @param bangumiTools Bangumi 工具
     * @param webSearchTools Web 搜索工具
     * @param localLibraryTools 本地记录查询工具
     * @param memoryService 长期记忆服务
     * @param operationService AI 写库操作服务
     * @param recordRepo 记录仓储
     * @param workRepo 作品仓储
     */
    public AiAutonomousTools(AiChatClientFactory chatClientFactory,
                             AiBangumiTools bangumiTools,
                             AiWebSearchTools webSearchTools,
                             AiLocalLibraryTools localLibraryTools,
                             AiPreferenceMemoryService memoryService,
                             AiWorkOperationService operationService,
                             RecordRepository recordRepo,
                             WorkRepository workRepo) {
        this.localLibraryTools = localLibraryTools;
        this.memoryService = memoryService;
        this.operationService = operationService;
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
        this.searchPipeline = new SearchPipeline(chatClientFactory::currentClient,
                chatClientFactory::cleanAssistantContent, bangumiTools, webSearchTools);
    }

    /**
     * 使用推荐/搜索流水线查找作品候选。
     *
     * @param query 查询或推荐需求
     * @param mode search / recommend / description
     * @return 候选作品卡片数据，不自动落库
     */
    public List<MatchedEntryDTO> findWorks(String query, String mode) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        context.listener().status("正在寻找作品");
        TokenUsageAdvisor.setCurrentNode("tool-findWorks");
        String normalizedMode = mode != null ? mode.trim() : "search";
        String input = query + "\n\n模式：" + normalizedMode;
        try {
            return searchPipeline.execute(input, "", context.listener()).cards();
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 将候选作品展示为 AI 卡片，不写入用户记录。
     *
     * @param subjectIds 作品 ID 列表
     * @param reason 展示理由
     * @return 创建的 PENDING 卡片列表
     */
    public List<ConversationCardDTO> presentWorks(List<Long> subjectIds, String reason) {
        AiToolContextHolder.require();
        if (subjectIds == null || subjectIds.isEmpty()) {
            return List.of();
        }
        TokenUsageAdvisor.setCurrentNode("tool-presentWorks");
        try {
            return subjectIds.stream()
                    .distinct()
                    .limit(8)
                    .map(id -> operationService.presentWork(id, reason))
                    .toList();
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 标记或修改作品记录。
     *
     * @param subjectId 作品 ID
     * @param status 目标状态
     * @param rating 评分
     * @param review 影评
     * @param reason 操作原因
     * @return 保存后的卡片
     */
    public ConversationCardDTO markWork(Long subjectId, String status, Double rating, String review, String reason) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId required");
        }
        TokenUsageAdvisor.setCurrentNode("tool-markWork");
        try {
            return operationService.markWork(subjectId, status, rating, review, reason);
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 取消本地作品标记。
     *
     * @param subjectId 作品 ID
     * @param reason 操作原因
     * @return 取消标记卡片；本地不存在时返回 null
     */
    public ConversationCardDTO unmarkWork(Long subjectId, String reason) {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId required");
        }
        TokenUsageAdvisor.setCurrentNode("tool-unmarkWork");
        try {
            return operationService.unmarkWork(subjectId, reason);
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 查询单个作品当前本地状态。
     *
     * @param subjectId 作品 ID
     * @return 当前状态摘要；不存在时返回空摘要
     */
    public WorkState getWorkState(Long subjectId) {
        TokenUsageAdvisor.setCurrentNode("tool-getWorkState");
        if (subjectId == null) {
            return new WorkState(null, null, null, null, null, false);
        }
        Work work = workRepo.findById(subjectId).orElse(null);
        Record record = recordRepo.findLatestByWorkId(subjectId).orElse(null);
        try {
            return new WorkState(
                    subjectId,
                    work != null ? displayName(work) : null,
                    record != null ? record.getStatus() : null,
                    record != null ? record.getRating() : null,
                    record != null ? record.getReview() : null,
                    record != null
            );
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 按关键词查询本地已有记录。
     *
     * @param keyword 关键词
     * @return 本地记录列表
     */
    public List<LocalRecordDTO> searchLocal(String keyword) {
        TokenUsageAdvisor.setCurrentNode("tool-searchLocal");
        try {
            return localLibraryTools.searchLocal(keyword);
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 按需读取长期记忆。
     *
     * @param query 当前用户需求
     * @return 可注入 Agent 的记忆摘要
     */
    public String readUserMemory(String query) {
        AiToolContextHolder.require();
        TokenUsageAdvisor.setCurrentNode("tool-readUserMemory");
        try {
            String memory = memoryService.getMemoryContext();
            return memory == null || memory.isBlank() ? "当前没有可用长期记忆。" : memory;
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    private static String displayName(Work work) {
        return work.getNameCn() != null && !work.getNameCn().isBlank() ? work.getNameCn() : work.getName();
    }

    /**
     * 单个作品当前状态摘要。
     */
    public record WorkState(Long subjectId, String nameCn, String status, Double rating, String review, boolean exists) {
    }
}

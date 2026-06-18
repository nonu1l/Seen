package com.nonu1l.media.agent.tool;

import com.nonu1l.media.agent.SearchPipeline;
import com.nonu1l.media.config.TokenUsageAdvisor;
import com.nonu1l.media.model.dto.ConversationCardDTO;
import com.nonu1l.media.model.dto.AgentFindWorksResultDTO;
import com.nonu1l.media.model.dto.AgentWorkActionResultDTO;
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
     * @param memoryService 长期记忆服务
     * @param operationService AI 写库操作服务
     * @param recordRepo 记录仓储
     * @param workRepo 作品仓储
     */
    public AiAutonomousTools(AiChatClientFactory chatClientFactory,
                             AiBangumiTools bangumiTools,
                             AiWebSearchTools webSearchTools,
                             AiPreferenceMemoryService memoryService,
                             AiWorkOperationService operationService,
                             RecordRepository recordRepo,
                             WorkRepository workRepo) {
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
     * @return 带成功状态和失败说明的找片结果
     */
    public AgentFindWorksResultDTO findWorks(String query, String mode) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        if (query == null || query.isBlank()) {
            return new AgentFindWorksResultDTO(false, query, normalizeMode(mode), List.of(),
                    "query is blank", "请先从用户请求中提取明确的作品搜索或推荐需求。");
        }
        context.listener().status("正在寻找作品");
        TokenUsageAdvisor.setCurrentNode("tool-findWorks");
        String normalizedMode = normalizeMode(mode);
        String input = query + "\n\n模式：" + normalizedMode;
        try {
            SearchPipeline.PipelineResult result = searchPipeline.execute(input, "", context.listener());
            if (result.cards() == null || result.cards().isEmpty()) {
                String reason = result.failReason() != null && !result.failReason().isBlank()
                        ? result.failReason() : "未找到匹配的影视作品";
                return new AgentFindWorksResultDTO(false, query, normalizedMode, List.of(), reason,
                        "可以换关键词、缩小条件，或向用户说明没有找到可靠候选。");
            }
            return new AgentFindWorksResultDTO(true, query, normalizedMode, result.cards(), null, null);
        } catch (Exception e) {
            return new AgentFindWorksResultDTO(false, query, normalizedMode, List.of(),
                    "findWorks failed: " + e.getMessage(), "可以换一种搜索表达，或告知用户当前搜索流程失败。");
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 将候选作品展示为 AI 卡片，不写入用户记录。
     *
     * @param subjectIds 作品 ID 列表
     * @param reason 展示理由
     * @return 展示操作结构化结果
     */
    public AgentWorkActionResultDTO presentWorks(List<Long> subjectIds, String reason) {
        AiToolContextHolder.require();
        if (subjectIds == null || subjectIds.isEmpty()) {
            return new AgentWorkActionResultDTO(false, "present", null, null, List.of(),
                    "subjectIds is empty", "请先通过 searchBangumi 或 findWorks 获得 subjectId。");
        }
        TokenUsageAdvisor.setCurrentNode("tool-presentWorks");
        try {
            List<ConversationCardDTO> cards = subjectIds.stream()
                    .distinct()
                    .limit(8)
                    .map(id -> operationService.presentWork(id, reason))
                    .toList();
            if (cards.isEmpty()) {
                return new AgentWorkActionResultDTO(false, "present", null, null, List.of(),
                        "no cards created", "请检查 subjectId 是否有效，或重新搜索候选作品。");
            }
            return new AgentWorkActionResultDTO(true, "present", null, null, cards, null, null);
        } catch (Exception e) {
            return new AgentWorkActionResultDTO(false, "present", null, null, List.of(),
                    "presentWorks failed: " + e.getMessage(), "请重新确认 subjectId，或改为只用自然语言说明候选。");
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
     * @return 标记操作结构化结果
     */
    public AgentWorkActionResultDTO markWork(Long subjectId, String status, Double rating, String review, String reason) {
        if (subjectId == null) {
            return new AgentWorkActionResultDTO(false, "mark", null, null, null,
                    "subjectId required", "请先调用 searchBangumi、findWorks 或 searchLocal 获取准确 subjectId。");
        }
        TokenUsageAdvisor.setCurrentNode("tool-markWork");
        try {
            ConversationCardDTO card = operationService.markWork(subjectId, status, rating, review, reason);
            return new AgentWorkActionResultDTO(true, "mark", subjectId, card, null, null, null);
        } catch (Exception e) {
            return new AgentWorkActionResultDTO(false, "mark", subjectId, null, null,
                    "markWork failed: " + e.getMessage(), "请重新确认 subjectId、状态和评分，必要时先查询作品当前状态。");
        } finally {
            TokenUsageAdvisor.setCurrentNode("autonomous-agent");
        }
    }

    /**
     * 取消本地作品标记。
     *
     * @param subjectId 作品 ID
     * @param reason 操作原因
     * @return 取消标记操作结构化结果
     */
    public AgentWorkActionResultDTO unmarkWork(Long subjectId, String reason) {
        if (subjectId == null) {
            return new AgentWorkActionResultDTO(false, "unmark", null, null, null,
                    "subjectId required", "取消标记前必须先调用 searchLocal 找到本地已有记录。");
        }
        TokenUsageAdvisor.setCurrentNode("tool-unmarkWork");
        try {
            ConversationCardDTO card = operationService.unmarkWork(subjectId, reason);
            if (card == null) {
                return new AgentWorkActionResultDTO(false, "unmark", subjectId, null, null,
                        "本地没有可取消的记录", "请不要去 Bangumi 搜索新条目；直接告诉用户本地没有该记录。");
            }
            return new AgentWorkActionResultDTO(true, "unmark", subjectId, card, null, null, null);
        } catch (Exception e) {
            return new AgentWorkActionResultDTO(false, "unmark", subjectId, null, null,
                    "unmarkWork failed: " + e.getMessage(), "请先调用 searchLocal 确认本地记录是否存在。");
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

    private static String normalizeMode(String mode) {
        return mode != null && !mode.isBlank() ? mode.trim() : "search";
    }

    /**
     * 单个作品当前状态摘要。
     */
    public record WorkState(Long subjectId, String nameCn, String status, Double rating, String review, boolean exists) {
    }
}

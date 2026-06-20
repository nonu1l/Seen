package com.nonu1l.media.agent.tool;

import com.nonu1l.media.agent.SearchPipeline;
import com.nonu1l.media.model.dto.ConversationCardDTO;
import com.nonu1l.media.model.dto.AgentFindWorksResultDTO;
import com.nonu1l.media.model.dto.AgentWorkActionResultDTO;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import com.nonu1l.media.service.AiPreferenceMemoryService;
import com.nonu1l.media.service.AiWorkOperationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
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
    private final AiToolSafetyService safetyService;
    private final int presentCardLimit;

    /**
     * 创建自主 Agent 工具集合。
     *
     * @param memoryService 长期记忆服务
     * @param operationService AI 写库操作服务
     * @param recordRepo 记录仓储
     * @param workRepo 作品仓储
     * @param searchPipeline 推荐/搜索流水线
     * @param safetyService AI 工具安全策略
     * @param presentCardLimit presentWorks 单次最多生成的展示卡片数量
     */
    public AiAutonomousTools(AiPreferenceMemoryService memoryService,
                             AiWorkOperationService operationService,
                             RecordRepository recordRepo,
                             WorkRepository workRepo,
                             SearchPipeline searchPipeline,
                             AiToolSafetyService safetyService,
                             @Value("${app.runtime.agent.present-card-limit:8}") int presentCardLimit) {
        this.memoryService = memoryService;
        this.operationService = operationService;
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
        this.searchPipeline = searchPipeline;
        this.safetyService = safetyService;
        this.presentCardLimit = presentCardLimit;
    }

    /**
     * 使用推荐/搜索流水线查找作品候选。
     *
     * @param query 查询或推荐需求
     * @param mode search / recommend / description
     * @return 带成功状态和失败说明的找片结果
     */
    @Tool(name = "findWorks", description = "根据推荐、搜索或描述找片需求查找影视作品候选")
    public AgentFindWorksResultDTO findWorks(
            @ToolParam(description = "查询、推荐或描述找片需求") String query,
            @ToolParam(description = "工具模式：search / recommend / description", required = false) String mode) {
        AiToolExecutionContext context = AiToolContextHolder.require();
        if (query == null || query.isBlank()) {
            return new AgentFindWorksResultDTO(false, query, normalizeMode(mode), List.of(),
                    "query is blank", "请先从用户请求中提取明确的作品搜索或推荐需求。");
        }
        context.listener().status("正在寻找作品");
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
        }
    }

    /**
     * 将候选作品展示为 AI 卡片，不写入用户记录。
     *
     * @param subjectIds 作品 ID 列表
     * @param reason 展示理由
     * @return 展示操作结构化结果
     */
    @Tool(name = "presentWorks", description = "把候选作品保存为 AI 页面 PENDING 展示卡片，不写入用户观看记录")
    public AgentWorkActionResultDTO presentWorks(
            @ToolParam(description = "要展示的 Bangumi subjectId 列表") List<Long> subjectIds,
            @ToolParam(description = "展示理由", required = false) String reason) {
        AiToolContextHolder.require();
        if (subjectIds == null || subjectIds.isEmpty()) {
            return new AgentWorkActionResultDTO(false, "present", null, null, List.of(),
                    "subjectIds is empty", "请先通过 searchBangumi 或 findWorks 获得 subjectId。");
        }
        try {
            List<ConversationCardDTO> cards = subjectIds.stream()
                    .distinct()
                    .limit(Math.max(1, presentCardLimit))
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
    @Tool(name = "markWork", description = "直接标记、评分或修改影评；会保存记录并生成可撤销 SAVED 卡片")
    public AgentWorkActionResultDTO markWork(
            @ToolParam(description = "Bangumi subjectId") Long subjectId,
            @ToolParam(description = "目标状态，如 wish / doing / done / on_hold / dropped", required = false) String status,
            @ToolParam(description = "0 到 10 的评分，可带 0.5", required = false) Double rating,
            @ToolParam(description = "用户影评或短评", required = false) String review,
            @ToolParam(description = "操作原因", required = false) String reason)
    {
        if (subjectId == null) {
            return new AgentWorkActionResultDTO(false, "mark", null, null, null,
                    "subjectId required", "请先调用 searchBangumi、findWorks 或 searchLocal 获取准确 subjectId。");
        }
        try {
            ConversationCardDTO card = operationService.markWork(subjectId, status, rating, review, reason);
            return new AgentWorkActionResultDTO(true, "mark", subjectId, card, null, null, null);
        } catch (Exception e) {
            return new AgentWorkActionResultDTO(false, "mark", subjectId, null, null,
                    "markWork failed: " + e.getMessage(), "请重新确认 subjectId、状态和评分，必要时先查询作品当前状态。");
        }
    }

    /**
     * 取消本地作品标记。
     *
     * @param subjectId 作品 ID
     * @param reason 操作原因
     * @return 取消标记操作结构化结果
     */
    @Tool(name = "unmarkWork", description = "取消本地已有作品标记；会删除作品记录并生成可撤回 UNMARKED 卡片")
    public AgentWorkActionResultDTO unmarkWork(
            @ToolParam(description = "要取消标记的 Bangumi subjectId") Long subjectId,
            @ToolParam(description = "操作原因", required = false) String reason) {
        if (subjectId == null) {
            return new AgentWorkActionResultDTO(false, "unmark", null, null, null,
                    "subjectId required", "取消标记前必须先调用 searchLocal 找到本地已有记录。");
        }
        AiToolExecutionContext context = AiToolContextHolder.require();
        AiToolSafetyService.SafetyDecision safety = safetyService.checkUnmarkAllowed(context);
        if (!safety.allowed()) {
            return new AgentWorkActionResultDTO(false, "unmark", subjectId, null, null,
                    safety.error(), safety.hint());
        }
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
        }
    }

    /**
     * 查询单个作品当前本地状态。
     *
     * @param subjectId 作品 ID
     * @return 当前状态摘要；不存在时返回空摘要
     */
    @Tool(name = "getWorkState", description = "按 Bangumi subjectId 查询单个本地作品当前状态")
    public WorkState getWorkState(
            @ToolParam(description = "Bangumi subjectId") Long subjectId) {
        if (subjectId == null) {
            return new WorkState(null, null, null, null, null, false);
        }
        Work work = workRepo.findById(subjectId).orElse(null);
        Record record = recordRepo.findLatestByWorkId(subjectId).orElse(null);
        return new WorkState(
                subjectId,
                work != null ? displayName(work) : null,
                record != null ? record.getStatus() : null,
                record != null ? record.getRating() : null,
                record != null ? record.getReview() : null,
                record != null
        );
    }

    /**
     * 按需读取长期记忆。
     *
     * @param query 当前用户需求
     * @return 可注入 Agent 的记忆摘要
     */
    @Tool(name = "readUserMemory", description = "按需读取用户长期偏好记忆；推荐时可参考但当前用户请求优先")
    public String readUserMemory(
            @ToolParam(description = "当前用户需求", required = false) String query) {
        AiToolContextHolder.require();
        String memory = memoryService.getMemoryContext();
        return memory == null || memory.isBlank() ? "当前没有可用长期记忆。" : memory;
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

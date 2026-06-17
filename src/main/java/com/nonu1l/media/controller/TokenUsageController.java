package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.TokenUsageDetailDTO;
import com.nonu1l.media.model.dto.TokenUsageTreeNodeDTO;
import com.nonu1l.media.model.entity.TokenUsage;
import com.nonu1l.media.repository.TokenUsageRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 后台管理接口：展示 Token 用量页面并提供树状汇总及明细查询 API。
 */
@RestController
public class TokenUsageController {

    private final TokenUsageRepository repo;

    /**
     * 注入 Token 用量仓储。
     *
     * @param repo token 用量持久化仓库。
     */
    public TokenUsageController(TokenUsageRepository repo) {
        this.repo = repo;
    }

    /**
     * 返回 Token 用量管理 HTML 页面。
     *
     * @return 页面 HTML 文本。
     * @throws Exception 读取模板文件失败时抛出。
     */
    @GetMapping(value = "/admin/token-usage", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page() throws Exception {
        return new String(new ClassPathResource("admin-pages/token-usage.html")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 按会话与轮次聚合 Token 用量，构建树状列表。
     *
     * <p>返回数据包含会话节点、回合节点与模型调用节点，并在节点上带上
     * prompt / completion / total 的汇总统计。</p>
     *
     * @return 树形结构 token 用量数据。
     */
    @GetMapping("/api/admin/token-usage/tree")
    public List<TokenUsageTreeNodeDTO> getTree() {
        List<TokenUsage> all = repo.findAll(Sort.by(Sort.Direction.ASC, "sessionId", "turn", "id"));

        Map<Long, List<TokenUsage>> bySession = all.stream()
                .filter(t -> t.getSessionId() != null)
                .collect(Collectors.groupingBy(TokenUsage::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<TokenUsageTreeNodeDTO> sessions = new ArrayList<>();
        for (var entry : bySession.entrySet()) {
            Long sessionId = entry.getKey();
            List<TokenUsage> records = entry.getValue();

            var firstTs = records.stream().map(TokenUsage::getCreatedAt)
                    .filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
            String dateStr = firstTs != null ? firstTs.toString().substring(0, 10) : "";

            Map<Integer, List<TokenUsage>> byTurn = records.stream()
                    .collect(Collectors.groupingBy(
                            t -> t.getTurn() != null ? t.getTurn() : 0,
                            LinkedHashMap::new, Collectors.toList()));

            var turnNodes = new ArrayList<TokenUsageTreeNodeDTO>();
            for (var te : byTurn.entrySet()) {
                int turn = te.getKey();
                List<TokenUsage> turnRecs = te.getValue();

                var nodeMap = turnRecs.stream()
                        .collect(Collectors.groupingBy(
                                t -> t.getNodeName() != null ? t.getNodeName() : "-",
                                LinkedHashMap::new, Collectors.toList()));

                var nodeChildren = new ArrayList<TokenUsageTreeNodeDTO>();
                for (var ne : nodeMap.entrySet()) {
                    nodeChildren.add(leaf("node/" + sessionId + "/" + turn + "/" + ne.getKey(),
                            ne.getKey(), ne.getValue()));
                }

                // 按 totalTokens 降序
//               nodeChildren.sort((a, b) -> Long.compare(b.totalTokens(), a.totalTokens()));

                turnNodes.add(new TokenUsageTreeNodeDTO(
                        "turn/" + sessionId + "/" + turn, "Turn " + turn,
                        sumTotal(turnRecs), sumPrompt(turnRecs), sumCompletion(turnRecs),
                        turnRecs.size(), nodeChildren));
            }

            sessions.add(new TokenUsageTreeNodeDTO(
                    "session/" + sessionId,
                    "Session #" + sessionId + "  " + dateStr,
                    sumTotal(records), sumPrompt(records), sumCompletion(records),
                    records.size(), dateStr, turnNodes));
        }

        return sessions;
    }

    /**
     * 查询某一会话回合、节点的 token 明细。
     *
     * @param sessionId 会话 ID，必填。
     * @param turn      对话轮次，必填。
     * @param node      节点名，必填。
     * @return 对应明细列表，按记录 ID 升序排序返回。
     */
    @GetMapping("/api/admin/token-usage/detail")
    public List<TokenUsageDetailDTO> getDetail(@RequestParam Long sessionId,
                                             @RequestParam Integer turn,
                                             @RequestParam String node) {
        return repo.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(t -> sessionId.equals(t.getSessionId()))
                .filter(t -> turn.equals(t.getTurn()))
                .filter(t -> node.equals(t.getNodeName()))
                .map(t -> new TokenUsageDetailDTO(t.getId(),
                        t.getProfileName(),
                        t.getModelName(),
                        t.getTotalTokens() != null ? t.getTotalTokens().longValue() : 0L,
                        t.getInputText(),
                        t.getOutputText()))
                .toList();
    }

    private TokenUsageTreeNodeDTO leaf(String key, String name, List<TokenUsage> recs) {
        return new TokenUsageTreeNodeDTO(key, name,
                sumTotal(recs), sumPrompt(recs), sumCompletion(recs), recs.size());
    }

    private long sumTotal(List<TokenUsage> list) {
        return list.stream().mapToLong(t -> t.getTotalTokens() != null ? t.getTotalTokens() : 0).sum();
    }

    private long sumPrompt(List<TokenUsage> list) {
        return list.stream().mapToLong(t -> t.getPromptTokens() != null ? t.getPromptTokens() : 0).sum();
    }

    private long sumCompletion(List<TokenUsage> list) {
        return list.stream().mapToLong(t -> t.getCompletionTokens() != null ? t.getCompletionTokens() : 0).sum();
    }
}

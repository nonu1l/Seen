package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.TokenUsageDetail;
import com.nonu1l.media.model.dto.TokenUsageTreeNode;
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

@RestController
public class TokenUsageController {

    private final TokenUsageRepository repo;

    public TokenUsageController(TokenUsageRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = "/admin/token-usage", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page() throws Exception {
        return new String(new ClassPathResource("admin-pages/token-usage.html")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @GetMapping("/api/admin/token-usage/tree")
    public List<TokenUsageTreeNode> getTree() {
        List<TokenUsage> all = repo.findAll(Sort.by(Sort.Direction.ASC, "sessionId", "turn", "id"));

        Map<Long, List<TokenUsage>> bySession = all.stream()
                .filter(t -> t.getSessionId() != null)
                .collect(Collectors.groupingBy(TokenUsage::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<TokenUsageTreeNode> sessions = new ArrayList<>();
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

            var turnNodes = new ArrayList<TokenUsageTreeNode>();
            for (var te : byTurn.entrySet()) {
                int turn = te.getKey();
                List<TokenUsage> turnRecs = te.getValue();

                var nodeMap = turnRecs.stream()
                        .collect(Collectors.groupingBy(
                                t -> t.getNodeName() != null ? t.getNodeName() : "-",
                                LinkedHashMap::new, Collectors.toList()));

                var nodeChildren = new ArrayList<TokenUsageTreeNode>();
                for (var ne : nodeMap.entrySet()) {
                    nodeChildren.add(leaf("node/" + sessionId + "/" + turn + "/" + ne.getKey(),
                            ne.getKey(), ne.getValue()));
                }

                // 按 totalTokens 降序
//               nodeChildren.sort((a, b) -> Long.compare(b.totalTokens(), a.totalTokens()));

                turnNodes.add(new TokenUsageTreeNode(
                        "turn/" + sessionId + "/" + turn, "Turn " + turn,
                        sumTotal(turnRecs), sumPrompt(turnRecs), sumCompletion(turnRecs),
                        turnRecs.size(), nodeChildren));
            }

            sessions.add(new TokenUsageTreeNode(
                    "session/" + sessionId,
                    "Session #" + sessionId + "  " + dateStr,
                    sumTotal(records), sumPrompt(records), sumCompletion(records),
                    records.size(), dateStr, turnNodes));
        }

        return sessions;
    }

    @GetMapping("/api/admin/token-usage/detail")
    public List<TokenUsageDetail> getDetail(@RequestParam Long sessionId,
                                             @RequestParam Integer turn,
                                             @RequestParam String node) {
        return repo.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .filter(t -> sessionId.equals(t.getSessionId()))
                .filter(t -> turn.equals(t.getTurn()))
                .filter(t -> node.equals(t.getNodeName()))
                .map(t -> new TokenUsageDetail(t.getId(),
                        t.getModelName(),
                        t.getTotalTokens() != null ? t.getTotalTokens().longValue() : 0L,
                        t.getInputText(),
                        t.getOutputText()))
                .toList();
    }

    private TokenUsageTreeNode leaf(String key, String name, List<TokenUsage> recs) {
        return new TokenUsageTreeNode(key, name,
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

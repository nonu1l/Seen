package com.nonu1l.media.agent.tool;

import com.nonu1l.media.repository.ConversationCardRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * AI 工具安全策略：在执行写库副作用前拦截高风险误操作。
 */
@Service
public class AiToolSafetyService {

    private static final int MAX_UNMARKS_PER_REQUEST = 5;
    private static final String UNMARK_ACTION = "UNMARK";

    private static final List<Pattern> WHOLE_LIBRARY_UNMARK_PATTERNS = List.of(
            Pattern.compile("(清空|清除|清掉)(我的)?(片库|影视库|媒体库|作品库|记录库|观看记录|收藏|所有记录|全部记录)"),
            Pattern.compile("(删除|删掉|移除|取消)(我的)?(所有|全部|全都|全部的|所有的)(记录|观看记录|标记|收藏|影视标记|影视记录|作品记录)"),
            Pattern.compile("(删除|删掉|移除|取消|取消标记|清空|清除|清掉)(我的)?(所有|全部|全都|全部的|所有的)(作品|影视|影片|电影|电视剧|动画|番剧)(记录|标记)?"),
            Pattern.compile("(所有|全部|全都|全部的|所有的)(记录|观看记录|标记|收藏|影视标记|影视记录|作品记录)(都|全都|全部)?(删除|删掉|移除|取消|取消标记|清空|清除|清掉)"),
            Pattern.compile("^(把|将|请|帮我|帮我把|麻烦|麻烦把|给我|给我把|我要|我想)?(我的)?(所有|全部|全都|全部的|所有的)(作品|影视|影片|电影|电视剧|动画|番剧)(记录|标记)?(都|全都|全部)?(删除|删掉|移除|取消|取消标记|清空|清除|清掉)$"),
            Pattern.compile("(整个|全)(片库|影视库|媒体库|作品库|记录库|库)(删除|删掉|移除|取消|取消标记|清空|清除|清掉)"),
            Pattern.compile("(删除|删掉|移除|取消|取消标记|清空|清除|清掉)(整个|全)(片库|影视库|媒体库|作品库|记录库|库)"),
            Pattern.compile("(片库|影视库|媒体库|作品库|记录库|库)里?(的)?(都|全都|全部)(删除|删掉|移除|取消|取消标记|清空|清除|清掉)"),
            Pattern.compile("^(把|将|请|帮我|帮我把|麻烦|麻烦把|给我|给我把|我要|我想)?(我的)?(全部|全都|所有)(取消|取消标记|删掉|删除|移除|清空)$")
    );

    private final ConversationCardRepository cardRepo;

    /**
     * 创建 AI 工具安全策略服务。
     *
     * @param cardRepo 会话卡片仓储，用于统计本轮请求已执行的动作数量
     */
    public AiToolSafetyService(ConversationCardRepository cardRepo) {
        this.cardRepo = cardRepo;
    }

    /**
     * 判断当前取消标记请求是否允许继续执行。
     *
     * @param context 当前工具执行上下文
     * @return 允许或拒绝的结构化结果
     */
    public SafetyDecision checkUnmarkAllowed(AiToolExecutionContext context) {
        if (context == null || context.sessionId() == null || context.requestId() == null || context.requestId().isBlank()) {
            return SafetyDecision.block(
                    "缺少 AI 工具执行上下文",
                    "不要执行写库操作；请在有效的 AI 会话请求内重试。");
        }
        String userInput = context.userInput();
        if (isWholeLibraryUnmarkRequest(userInput)) {
            return SafetyDecision.block(
                    "拒绝执行整库级取消标记请求",
                    "用户可能在请求清空整个片库或删除所有记录；不要调用 unmarkWork，改为说明只能按明确作品或系列处理。");
        }

        long existingUnmarks = cardRepo.countBySessionIdAndRequestIdAndActionType(
                context.sessionId(), context.requestId(), UNMARK_ACTION);
        if (existingUnmarks >= MAX_UNMARKS_PER_REQUEST) {
            return SafetyDecision.block(
                    "本轮取消标记数量已达到安全上限 5 个",
                    "停止继续取消标记，请让用户缩小范围或明确确认下一批作品。");
        }
        return SafetyDecision.allow();
    }

    /**
     * 识别整库级取消/删除表达，避免误伤带明确系列限定的“所有电影”等请求。
     *
     * @param input 用户原始输入
     * @return 命中整库级高风险表达时返回 true
     */
    boolean isWholeLibraryUnmarkRequest(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String normalized = input.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "")
                .replace('，', ',')
                .replace('。', '.')
                .replace('！', '!')
                .replace('？', '?');
        return WHOLE_LIBRARY_UNMARK_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * 工具安全策略判断结果。
     *
     * @param allowed 是否允许执行
     * @param error 拒绝原因
     * @param hint 给 Agent 的下一步提示
     */
    public record SafetyDecision(boolean allowed, String error, String hint) {
        /**
         * @return 允许执行的判断结果
         */
        public static SafetyDecision allow() {
            return new SafetyDecision(true, null, null);
        }

        /**
         * 创建拒绝执行的判断结果。
         *
         * @param error 拒绝原因
         * @param hint 给 Agent 的下一步提示
         * @return 拒绝执行的判断结果
         */
        public static SafetyDecision block(String error, String hint) {
            return new SafetyDecision(false, error, hint);
        }
    }
}

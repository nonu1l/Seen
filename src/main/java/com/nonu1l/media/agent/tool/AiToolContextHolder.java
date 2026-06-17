package com.nonu1l.media.agent.tool;

/**
 * 保存当前线程的自主 Agent 工具上下文。
 */
public final class AiToolContextHolder {

    private static final ThreadLocal<AiToolExecutionContext> CURRENT = new ThreadLocal<>();

    private AiToolContextHolder() {
    }

    /**
     * 设置当前工具执行上下文。
     *
     * @param context 单轮执行上下文
     */
    public static void set(AiToolExecutionContext context) {
        CURRENT.set(context);
    }

    /**
     * @return 当前工具执行上下文；未设置时抛出异常，避免写库工具脱离请求追踪
     */
    public static AiToolExecutionContext require() {
        AiToolExecutionContext context = CURRENT.get();
        if (context == null || context.requestId() == null || context.requestId().isBlank()) {
            throw new IllegalStateException("AI tool execution context is not available");
        }
        return context;
    }

    /**
     * 清理当前线程上下文，防止污染后续请求。
     */
    public static void clear() {
        CURRENT.remove();
    }
}

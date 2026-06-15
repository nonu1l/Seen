package com.nonu1l.media.agent;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 状态容器：基于 LangGraph4j AgentState，定义图执行需要的状态键和值通道。
 */
public class SeenAgentState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "userInput",        Channels.base(() -> ""),
        "history",          Channels.base(() -> ""),
        "intent",           Channels.base(() -> "analyze"),
        "searchResults",    Channels.base(ArrayList::new),
        "trendingResults",  Channels.base(ArrayList::new),
        "cards",            Channels.base(ArrayList::new),
        "replyText",        Channels.base(() -> ""),
        "retryCount",       Channels.base(() -> 0),
        "unmarkIds",        Channels.base(ArrayList::new)
    );

    /**
     * 使用图节点返回的键值映射初始化状态。
     *
     * @param data 节点产出数据，缺失字段会走 SCHEMA 默认值
     */
    public SeenAgentState(Map<String, Object> data) {
        super(data);
    }

    /**
     * @return 当前用户输入
     */
    public String userInput()  { return this.<String>value("userInput").orElse(""); }
    /**
     * @return 会话历史文本
     */
    public String history()    { return this.<String>value("history").orElse(""); }
    /**
     * @return 本轮意图分类结果
     */
    public String intent()     { return this.<String>value("intent").orElse("analyze"); }
    /**
     * @return 重试次数（目前用于控制兼容回退逻辑）
     */
    public int retryCount()    { return this.<Integer>value("retryCount").orElse(0); }
    /**
     * @return 最终回复文本
     */
    public String replyText()  { return this.<String>value("replyText").orElse(""); }

    /**
     * @param <T> 泛型卡片元素类型
     * @return 搜索结果列表（默认空列表）
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> searchResults()   { return (List<T>) this.value("searchResults").orElse(List.of()); }

    /**
     * @param <T> 泛型卡片元素类型
     * @return 趋势结果列表（默认空列表）
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> trendingResults() { return (List<T>) this.value("trendingResults").orElse(List.of()); }

    /**
     * @param <T> 泛型卡片元素类型
     * @return Agent 识别到的卡片列表（默认空列表）
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> cards()           { return (List<T>) this.value("cards").orElse(List.of()); }

    /**
     * @param <T> 泛型元素类型
     * @return 需要取消标记的 subjectId 列表（默认空列表）
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> unmarkIds()       { return (List<T>) this.value("unmarkIds").orElse(List.of()); }
}

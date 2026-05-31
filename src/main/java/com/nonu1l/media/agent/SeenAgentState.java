package com.nonu1l.media.agent;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent Graph 状态 — 继承 LangGraph4j AgentState，只读访问器，值通过节点返回的 Map 更新。
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
        "retryCount",       Channels.base(() -> 0)
    );

    public SeenAgentState(Map<String, Object> data) {
        super(data);
    }

    public String userInput()  { return this.<String>value("userInput").orElse(""); }
    public String history()    { return this.<String>value("history").orElse(""); }
    public String intent()     { return this.<String>value("intent").orElse("analyze"); }
    public int retryCount()    { return this.<Integer>value("retryCount").orElse(0); }
    public String replyText()  { return this.<String>value("replyText").orElse(""); }

    @SuppressWarnings("unchecked")
    public <T> List<T> searchResults()   { return (List<T>) this.value("searchResults").orElse(List.of()); }

    @SuppressWarnings("unchecked")
    public <T> List<T> trendingResults() { return (List<T>) this.value("trendingResults").orElse(List.of()); }

    @SuppressWarnings("unchecked")
    public <T> List<T> cards()           { return (List<T>) this.value("cards").orElse(List.of()); }
}

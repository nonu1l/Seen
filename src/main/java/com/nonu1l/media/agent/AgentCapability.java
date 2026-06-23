package com.nonu1l.media.agent;

/**
 * Agent 子流程能力类型；用于把用户请求分流到受限工具 Runner。
 */
public enum AgentCapability {
    WATCH_SOURCE,
    FIND_WORKS,
    ANALYSIS,
    ACTION,
    GENERAL
}

package com.nonu1l.media.model.dto;

/**
 * LLM 解析请求体（已弃用，仅保留代码）。
 * 原用于接收用户自然语言输入，现不再使用。
 */
public class ParseRequest {

    /** 用户原始输入文本 */
    private String input;

    /**
     * 默认构造方法。
     */
    public ParseRequest() {
    }

    /**
     * 使用输入文本创建解析请求。
     *
     * @param input 用户原始输入文本。
     */
    public ParseRequest(String input) {
        this.input = input;
    }

    /**
     * 获取用户原始输入文本。
     *
     * @return 用户原始输入文本。
     */
    public String getInput() {
        return input;
    }

    /**
     * 设置用户原始输入文本。
     *
     * @param input 用户原始输入文本。
     */
    public void setInput(String input) {
        this.input = input;
    }
}

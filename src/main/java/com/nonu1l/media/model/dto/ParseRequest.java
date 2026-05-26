package com.nonu1l.media.model.dto;

/**
 * LLM 解析请求体（已弃用，仅保留代码）。
 * 原用于接收用户自然语言输入，现不再使用。
 */
public class ParseRequest {

    /** 用户原始输入文本 */
    private String input;

    public ParseRequest() {
    }

    public ParseRequest(String input) {
        this.input = input;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }
}

package com.nonu1l.media.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为独立 Spring Bean 的 public 方法添加 Caffeine 请求缓存。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CaffeineRequestCache {

    /**
     * SpEL 缓存 key 表达式。
     */
    String key();

    /**
     * SpEL TTL 表达式，单位秒。
     */
    String ttlSeconds();
}

package com.nonu1l.media.cache;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 处理 {@link CaffeineRequestCache} 注解的缓存切面。
 */
@Aspect
@Component
public class CaffeineRequestCacheAspect {

    private static final Logger log = LoggerFactory.getLogger(CaffeineRequestCacheAspect.class);

    private final RequestCacheStore cacheStore;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * @param cacheStore 缓存存储
     */
    public CaffeineRequestCacheAspect(RequestCacheStore cacheStore) {
        this.cacheStore = cacheStore;
    }

    /**
     * 环绕执行带缓存注解的方法。
     *
     * @param joinPoint 调用点
     * @param annotation 缓存注解
     * @return 缓存命中值或原方法返回值
     * @throws Throwable 原方法异常
     */
    @Around("@annotation(annotation)")
    public Object cacheRequest(ProceedingJoinPoint joinPoint, CaffeineRequestCache annotation) throws Throwable {
        Method method = mostSpecificMethod(joinPoint);
        EvaluationContext context = evaluationContext(joinPoint, method);
        String key = String.valueOf(parser.parseExpression(annotation.key()).getValue(context));
        long ttlSeconds = resolveTtlSeconds(annotation, context);

        Optional<String> cached = cacheStore.get(key);
        if (cached.isPresent()) {
            log.debug("request cache HIT key={}", key);
            return cached.get();
        }

        log.debug("request cache MISS key={}", key);
        Object result = joinPoint.proceed();
        if (result instanceof String body) {
            cacheStore.put(key, body, ttlSeconds);
            log.debug("request cache saved key={} ttl={}s", key, ttlSeconds);
        }
        return result;
    }

    private Method mostSpecificMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return AopUtils.getMostSpecificMethod(signature.getMethod(), joinPoint.getTarget().getClass());
    }

    private EvaluationContext evaluationContext(ProceedingJoinPoint joinPoint, Method method) {
        StandardEvaluationContext context = new StandardEvaluationContext(joinPoint.getTarget());
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return context;
    }

    private long resolveTtlSeconds(CaffeineRequestCache annotation, EvaluationContext context) {
        Object value = parser.parseExpression(annotation.ttlSeconds()).getValue(context);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}

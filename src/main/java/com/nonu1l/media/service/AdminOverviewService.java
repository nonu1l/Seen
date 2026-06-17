package com.nonu1l.media.service;

import com.nonu1l.media.cache.RequestCacheStore;
import com.nonu1l.media.model.dto.AdminOverviewDTO;
import com.nonu1l.media.repository.TokenUsageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台额外配置页的汇总与维护服务。
 */
@Service
public class AdminOverviewService {

    private final TokenUsageRepository tokenUsageRepository;
    private final RequestCacheStore requestCacheStore;

    /**
     * 创建后台汇总服务。
     *
     * @param tokenUsageRepository Token 用量仓储
     * @param requestCacheStore 请求缓存存储
     */
    public AdminOverviewService(TokenUsageRepository tokenUsageRepository,
                                RequestCacheStore requestCacheStore) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.requestCacheStore = requestCacheStore;
    }

    /**
     * 读取当前后台汇总数据。
     *
     * @return Token 总量与缓存字节数
     */
    public AdminOverviewDTO overview() {
        Long totalTokens = tokenUsageRepository.sumTotalTokens();
        return new AdminOverviewDTO(totalTokens != null ? totalTokens : 0L,
                requestCacheStore.totalBytes());
    }

    /**
     * 清空当前进程内请求缓存并返回最新汇总。
     *
     * @return 清空后的汇总数据
     */
    public AdminOverviewDTO clearRequestCache() {
        requestCacheStore.clear();
        return overview();
    }

    /**
     * 删除全部 Token 使用记录并返回最新汇总。
     *
     * @return 重置后的汇总数据
     */
    @Transactional
    public AdminOverviewDTO resetTokenUsage() {
        tokenUsageRepository.deleteAllInBatch();
        return overview();
    }
}

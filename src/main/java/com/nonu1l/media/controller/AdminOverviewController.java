package com.nonu1l.media.controller;

import com.nonu1l.media.model.dto.AdminOverviewResponse;
import com.nonu1l.media.service.AdminOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台额外配置汇总与维护接口。
 */
@RestController
public class AdminOverviewController {

    private final AdminOverviewService overviewService;

    /**
     * 创建后台额外配置控制器。
     *
     * @param overviewService 后台汇总服务
     */
    public AdminOverviewController(AdminOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    /**
     * 查询 Token 与请求缓存总量。
     *
     * @return 后台轻量汇总
     */
    @GetMapping("/api/admin/overview")
    public AdminOverviewResponse overview() {
        return overviewService.overview();
    }

    /**
     * 清空当前进程内请求缓存。
     *
     * @return 清空后的后台轻量汇总
     */
    @PostMapping("/api/admin/request-cache/clear")
    public AdminOverviewResponse clearRequestCache() {
        return overviewService.clearRequestCache();
    }

    /**
     * 删除全部 Token 使用明细。
     *
     * @return 重置后的后台轻量汇总
     */
    @PostMapping("/api/admin/token-usage/reset")
    public AdminOverviewResponse resetTokenUsage() {
        return overviewService.resetTokenUsage();
    }
}

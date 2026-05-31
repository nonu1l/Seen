//package com.nonu1l.media.service;
//
//import java.util.List;
//
///**
// * 测试 WebSearch：命令行传入关键词，输出解析结果。
// * 在 IDE 中 run main()，参数填搜索关键词。
// */
//public class WebSearchDebug {
//    public static void main(String[] args) throws Exception {
//        String query = args.length > 0 ? args[0] : "2026年热门欧美剧推荐";
//        System.out.println("Searching: " + query);
//
//        var builder = new org.springframework.boot.web.client.RestTemplateBuilder();
//        var svc = new WebSearchService(builder, "https://seen.slimrip.com");
//
//        List<com.nonu1l.media.model.dto.WebSearchItem> results = svc.search(query);
//        System.out.println("Found: " + results.size() + " results\n");
//        for (int i = 0; i < results.size(); i++) {
//            var r = results.get(i);
//            System.out.printf("[%d] %s\n    %s\n    %s\n\n", i + 1, r.title(), r.snippet(), r.url());
//        }
//    }
//}

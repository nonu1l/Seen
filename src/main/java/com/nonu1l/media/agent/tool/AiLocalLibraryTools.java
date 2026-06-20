package com.nonu1l.media.agent.tool;

import com.nonu1l.media.model.dto.LocalWorkRecordDTO;
import com.nonu1l.media.model.entity.Record;
import com.nonu1l.media.model.entity.Work;
import com.nonu1l.media.repository.RecordRepository;
import com.nonu1l.media.repository.WorkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * AI 专用本地媒体库查询工具。
 */
@Component
public class AiLocalLibraryTools {

    private static final Logger log = LoggerFactory.getLogger(AiLocalLibraryTools.class);

    private final RecordRepository recordRepo;
    private final WorkRepository workRepo;

    /**
     * @param recordRepo 记录仓储
     * @param workRepo 作品仓储
     */
    public AiLocalLibraryTools(RecordRepository recordRepo, WorkRepository workRepo) {
        this.recordRepo = recordRepo;
        this.workRepo = workRepo;
    }

    /**
     * 搜索本地数据库中的作品。
     *
     * <p>返回有最新记录的本地条目，未有记录的作品不会展示为已追踪状态。</p>
     *
     * @param keyword 关键词，空白时返回全部
     * @return 紧凑本地记录列表
     */
    @Tool(name = "searchLocal", description = "查询本地已标记的作品记录")
    public List<LocalWorkRecordDTO> searchLocal(
            @ToolParam(description = "本地作品搜索关键词") String keyword) {
        log.debug("Tool: searchLocal keyword='{}'", keyword);
        List<Work> works = (keyword == null || keyword.isBlank())
                ? workRepo.findAll()
                : workRepo.searchByName(keyword.trim());

        List<LocalWorkRecordDTO> results = new ArrayList<>();
        for (Work w : works) {
            Optional<Record> r = recordRepo.findLatestByWorkId(w.getId());
            r.ifPresent(record -> results.add(new LocalWorkRecordDTO(
                    w.getId(), w.getNameCn() != null ? w.getNameCn() : w.getName(),
                    record.getStatus(),
                    record.getRating(),
                    record.getReview())));
        }
        return results;
    }
}

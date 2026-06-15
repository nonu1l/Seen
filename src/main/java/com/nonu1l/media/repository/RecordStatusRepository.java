package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 记录状态码仓储：维护作品记录状态字典（如 collect、wish、drop 等）。
 *
 * <p>供前端展示与业务流程共用状态语义，不承载变更规则计算。</p>
 */
@Repository
public interface RecordStatusRepository extends JpaRepository<RecordStatus, String> {
}

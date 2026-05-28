package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {

    /** 按 updatedAt 倒序取最新一条（用于展示） */
    @Query("SELECT r FROM Record r WHERE r.workId = :workId ORDER BY r.updatedAt DESC, r.id DESC LIMIT 1")
    Optional<Record> findLatestByWorkId(@Param("workId") Long workId);

    /** 批量获取每个作品的最新 Record */
    @Query("SELECT r FROM Record r WHERE r.id IN (SELECT MAX(r2.id) FROM Record r2 WHERE r2.workId IN :workIds GROUP BY r2.workId)")
    List<Record> findLatestByWorkIds(@Param("workIds") Set<Long> workIds);

    /** 取每个作品的最近两条 record，用于判断 AI 新增后是否有旧记录可对比 */
    @Query("SELECT r FROM Record r WHERE r.workId = :workId ORDER BY r.updatedAt DESC, r.id DESC LIMIT 2")
    List<Record> findLatest2ByWorkId(@Param("workId") Long workId);

    long countByWorkIdAndStatus(Long workId, String status);

    @Query("SELECT COUNT(r) FROM Record r WHERE r.workId = :workId")
    long countByWorkId(@Param("workId") Long workId);

    void deleteAllByWorkId(Long workId);

    /** 取最新一条 record 的 id */
    @Query(value = "SELECT id FROM record WHERE work_id = :workId ORDER BY updated_at DESC, id DESC LIMIT 1", nativeQuery = true)
    Optional<Long> findLatestIdByWorkId(@Param("workId") Long workId);

    /** 按 id 批量删除（不走 entity 加载） */
    @Modifying
    @Query("DELETE FROM Record r WHERE r.id = :id")
    void deleteRecordById(@Param("id") Long id);
}

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

/**
 * 作品记录仓储：提供作品追踪记录的查询、计数、删除能力。
 *
 * <p>除基础 CRUD 外，重点支持“按作品取最新记录/最近两条”和计数统计场景。</p>
 */
@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {

    /**
     * 按作品 ID 查询最近一条记录（按更新时间倒序）。
     *
     * @param workId 作品 ID
     * @return 对应作品的最新记录；若不存在则返回 {@link Optional#empty()}
     */
    @Query("SELECT r FROM Record r WHERE r.workId = :workId ORDER BY r.updatedAt DESC, r.id DESC LIMIT 1")
    Optional<Record> findLatestByWorkId(@Param("workId") Long workId);

    /**
     * 批量查询多个作品各自的最新记录（按每个作品最新 ID 反推）。
     *
     * @param workIds 作品 ID 集合
     * @return 每个作品的最新记录列表，按数据库执行返回顺序返回
     */
    @Query("SELECT r FROM Record r WHERE r.id IN (SELECT MAX(r2.id) FROM Record r2 WHERE r2.workId IN :workIds GROUP BY r2.workId)")
    List<Record> findLatestByWorkIds(@Param("workIds") Set<Long> workIds);

    /**
     * 查询最近更新的记录，用于长期偏好画像提取近期变化。
     *
     * @return 最近 30 条记录，按更新时间和主键倒序
     */
    List<Record> findTop30ByOrderByUpdatedAtDescIdDesc();

    /**
     * 查询单个作品最近两条记录，用于新增/覆盖前后版本对比。
     *
     * @param workId 作品 ID
     * @return 指定作品最近两条记录（最多2条），按更新时间降序
     */
    @Query("SELECT r FROM Record r WHERE r.workId = :workId ORDER BY r.updatedAt DESC, r.id DESC LIMIT 2")
    List<Record> findLatest2ByWorkId(@Param("workId") Long workId);

    /**
     * 按作品和状态统计记录数。
     *
     * @param workId 作品 ID
     * @param status 记录状态
     * @return 匹配作品且状态一致的记录数量
     */
    long countByWorkIdAndStatus(Long workId, String status);

    /**
     * 按作品统计全部记录数量。
     *
     * @param workId 作品 ID
     * @return 该作品下记录总数
     */
    @Query("SELECT COUNT(r) FROM Record r WHERE r.workId = :workId")
    long countByWorkId(@Param("workId") Long workId);

    /**
     * 删除某个作品下的全部记录。
     *
     * <p>关键副作用：会批量移除作品相关历史记录，无法直接回滚。</p>
     *
     * @param workId 作品 ID
     */
    void deleteAllByWorkId(Long workId);

    /**
     * 查询作品最近一条记录的 ID。
     *
     * @param workId 作品 ID
     * @return 最近一条记录的 ID；无记录则返回 {@link Optional#empty()}
     */
    @Query(value = "SELECT id FROM record WHERE work_id = :workId ORDER BY updated_at DESC, id DESC LIMIT 1", nativeQuery = true)
    Optional<Long> findLatestIdByWorkId(@Param("workId") Long workId);

    /**
     * 按主键删除记录（直接执行删除语句）。
     *
     * <p>关键副作用：该方法执行的是直接 DML 删除，未走实体生命周期回调。</p>
     *
     * @param id 记录 ID
     */
    @Modifying
    @Query("DELETE FROM Record r WHERE r.id = :id")
    void deleteRecordById(@Param("id") Long id);
}

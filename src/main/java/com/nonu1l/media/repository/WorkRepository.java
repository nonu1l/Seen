package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 作品仓储：提供作品信息与记录快照的组合查询。
 *
 * <p>聚焦于列表查询与检索场景，兼顾包含最新记录信息与名称关键字搜索。</p>
 */
@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

    /**
     * 联表查询作品及其最新记录快照，返回字段级原始投影。
     *
     * @return 每条记录包含作品字段、关联最新记录字段与观看人数统计
     */
    @Query(value = """
        SELECT w.id, w.name, w.name_cn, w.platform,
               w.cover_url, w.year, w.plot, w.score, w.tags_cache,
               w.created_at, w.updated_at,
               r.id AS rec_id, r.status, r.rating, r.review, r.created_at AS rec_created_at,
               (SELECT COUNT(*) FROM record rc WHERE rc.work_id = w.id AND rc.status = 'collect') AS watched_count
        FROM work w
        LEFT JOIN record r ON r.id = (
            SELECT MAX(r2.id) FROM record r2 WHERE r2.work_id = w.id
        )
        ORDER BY r.id DESC NULLS LAST
        """, nativeQuery = true)
    List<Object[]> findAllWithLatestRecord();


    /**
     * 查询所有有记录的作品并按其最新记录倒序排序（JPQL）。
     *
     * @return 含最新记录关联的作品列表
     */
    @Query("""
        SELECT w FROM Work w
        JOIN Record r ON r.id = (
            SELECT MAX(r2.id) FROM Record r2 WHERE r2.workId = w.id
        )
        ORDER BY r.id DESC
        """)
    List<Work> findAllOrderByLatestRecord();

    /**
     * 按名称关键词做中文名/原始名模糊查询。
     *
     * @param keyword 搜索关键字
     * @return 命中名称或中文名的作品列表
     */
    @Query("""
        SELECT w FROM Work w
        WHERE LOWER(COALESCE(w.nameCn, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(COALESCE(w.name, ''))   LIKE LOWER(CONCAT('%', :keyword, '%'))
        """)
    List<Work> searchByName(@Param("keyword") String keyword);
}

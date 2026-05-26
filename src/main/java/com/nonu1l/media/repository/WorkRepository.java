package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {

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
}

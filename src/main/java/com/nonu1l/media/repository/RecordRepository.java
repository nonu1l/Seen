package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecordRepository extends JpaRepository<Record, Long> {

    @Query("SELECT r FROM Record r WHERE r.workId = :workId ORDER BY r.id DESC LIMIT 1")
    Optional<Record> findLatestByWorkId(@Param("workId") Long workId);

    List<Record> findAllByWorkIdOrderByIdDesc(Long workId);

    long countByWorkIdAndStatus(Long workId, String status);

    @Query("SELECT COUNT(r) FROM Record r WHERE r.workId = :workId")
    long countByWorkId(@Param("workId") Long workId);

    void deleteAllByWorkId(Long workId);
}

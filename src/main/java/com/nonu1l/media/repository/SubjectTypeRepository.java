package com.nonu1l.media.repository;

import com.nonu1l.media.model.entity.SubjectType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Bangumi 条目类型仓储：管理类型字典（如动画、音乐、游戏等）基础码表。
 */
@Repository
public interface SubjectTypeRepository extends JpaRepository<SubjectType, Integer> {
}

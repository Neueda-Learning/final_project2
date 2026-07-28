package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Portfolio;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, String> {

    /** 列出指定用户的所有活跃（未归档）组合，分页返回 */
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId AND p.archived = false")
    Page<Portfolio> findByUserIdAndArchivedFalse(@Param("userId") String userId, Pageable pageable);

    /** 按 id + 用户 id 精确查询，保证用户隔离（其他用户的组合返回 empty） */
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    /** 检查该用户是否已有同名活跃组合（大小写不敏感），用于创建时的冲突校验 */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Portfolio p "
            + "WHERE p.user.id = :userId AND p.archived = false AND LOWER(p.name) = LOWER(:name)")
    boolean existsActiveByUserIdAndNameIgnoreCase(
            @Param("userId") String userId, @Param("name") String name);

    /**
     * 同上，但排除指定 id 的组合本身，用于 PATCH 更新时校验新名称是否与其他组合冲突
     * （允许将名称改为与自身相同的大小写变体）
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Portfolio p "
            + "WHERE p.user.id = :userId AND p.archived = false "
            + "AND LOWER(p.name) = LOWER(:name) AND p.id <> :excludeId")
    boolean existsActiveByUserIdAndNameIgnoreCaseExcluding(
            @Param("userId") String userId,
            @Param("name") String name,
            @Param("excludeId") String excludeId);
}

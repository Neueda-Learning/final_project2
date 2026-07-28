package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Portfolio;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, String> {

    /** Lists active portfolios for a user with pagination. */
    @Query("SELECT p FROM Portfolio p WHERE p.user.id = :userId AND p.archived = false")
    Page<Portfolio> findByUserIdAndArchivedFalse(@Param("userId") String userId, Pageable pageable);

    /** Finds by portfolio and user ID to preserve ownership isolation. */
    @Query("SELECT p FROM Portfolio p WHERE p.id = :id AND p.user.id = :userId")
    Optional<Portfolio> findByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    /** Checks for a case-insensitive active-name conflict during creation. */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Portfolio p "
            + "WHERE p.user.id = :userId AND p.archived = false AND LOWER(p.name) = LOWER(:name)")
    boolean existsActiveByUserIdAndNameIgnoreCase(
            @Param("userId") String userId, @Param("name") String name);

    /**
     * Checks for a name conflict while excluding the portfolio being updated.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Portfolio p "
            + "WHERE p.user.id = :userId AND p.archived = false "
            + "AND LOWER(p.name) = LOWER(:name) AND p.id <> :excludeId")
    boolean existsActiveByUserIdAndNameIgnoreCaseExcluding(
            @Param("userId") String userId,
            @Param("name") String name,
            @Param("excludeId") String excludeId);
}

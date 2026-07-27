package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Portfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, String> {

    Page<Portfolio> findByUserIdAndArchivedFalse(String userId, Pageable pageable);
}

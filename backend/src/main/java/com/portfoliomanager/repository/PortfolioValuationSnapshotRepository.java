package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.PortfolioValuationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioValuationSnapshotRepository
        extends JpaRepository<PortfolioValuationSnapshot, Long> {}

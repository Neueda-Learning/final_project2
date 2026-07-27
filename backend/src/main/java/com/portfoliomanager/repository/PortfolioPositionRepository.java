package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.PortfolioPosition;
import com.portfoliomanager.domain.model.PortfolioPositionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioPositionRepository
        extends JpaRepository<PortfolioPosition, PortfolioPositionId> {}

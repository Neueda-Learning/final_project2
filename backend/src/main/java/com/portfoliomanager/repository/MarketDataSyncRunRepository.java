package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.MarketDataSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDataSyncRunRepository extends JpaRepository<MarketDataSyncRun, String> {}

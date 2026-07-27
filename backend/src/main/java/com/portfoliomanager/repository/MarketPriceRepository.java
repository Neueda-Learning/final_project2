package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {}

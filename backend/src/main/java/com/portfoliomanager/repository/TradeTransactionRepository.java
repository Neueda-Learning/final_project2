package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.TradeTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeTransactionRepository extends JpaRepository<TradeTransaction, String> {}

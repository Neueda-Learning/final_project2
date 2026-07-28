package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Instrument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findByActiveTrueOrderBySymbol();

    /**
     * Search active instruments by asset type (STOCK or ETF).
     */
    @Query("SELECT i FROM Instrument i WHERE i.active = true AND UPPER(i.assetType) = UPPER(:assetType) ORDER BY i.symbol")
    List<Instrument> searchActiveByAssetType(@Param("assetType") String assetType);
}

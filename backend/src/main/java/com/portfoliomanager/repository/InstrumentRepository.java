package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Instrument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findByActiveTrueOrderBySymbol();

    /** Searches active instruments by a symbol or name fragment. */
    @Query("""
            SELECT i
            FROM Instrument i
            WHERE i.active = true
              AND (
                  UPPER(i.symbol) LIKE UPPER(CONCAT('%', :query, '%'))
                  OR UPPER(i.name) LIKE UPPER(CONCAT('%', :query, '%'))
              )
            ORDER BY
              CASE WHEN UPPER(i.symbol) = UPPER(:query) THEN 0 ELSE 1 END,
              i.symbol
            """)
    List<Instrument> searchActive(
            @Param("query") String query, org.springframework.data.domain.Pageable pageable);
}

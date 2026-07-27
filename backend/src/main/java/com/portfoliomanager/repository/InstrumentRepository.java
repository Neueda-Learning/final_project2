package com.portfoliomanager.repository;

import com.portfoliomanager.domain.model.Instrument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    List<Instrument> findByActiveTrueOrderBySymbol();
}

package com.portfoliomanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.domain.PriceStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class MarketCalendarServiceTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void beforeMondayCloseUsesPreviousFriday() {
        var calendar = calendarAt("2026-07-27T18:00:00Z");

        assertThat(calendar.latestExpectedTradingDay())
                .isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(calendar.status(LocalDate.of(2026, 7, 24)))
                .isEqualTo(PriceStatus.FRESH);
        assertThat(calendar.status(LocalDate.of(2026, 7, 23)))
                .isEqualTo(PriceStatus.STALE);
    }

    @Test
    void thanksgivingIsNotTreatedAsATradingDay() {
        var calendar = calendarAt("2026-11-27T15:00:00Z");

        assertThat(calendar.latestExpectedTradingDay())
                .isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void missingPriceIsUnavailable() {
        assertThat(calendarAt("2026-07-27T18:00:00Z").status(null))
                .isEqualTo(PriceStatus.UNAVAILABLE);
    }

    private MarketCalendarService calendarAt(String instant) {
        return new MarketCalendarService(
                NEW_YORK,
                LocalTime.of(16, 15),
                Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
    }
}

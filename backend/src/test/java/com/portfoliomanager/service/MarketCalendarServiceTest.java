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

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Test
    void beforeMondayCloseUsesPreviousFriday() {
        var calendar = calendarAt("2026-07-27T02:00:00Z");

        assertThat(calendar.latestExpectedTradingDay())
                .isEqualTo(LocalDate.of(2026, 7, 24));
        assertThat(calendar.status(LocalDate.of(2026, 7, 24)))
                .isEqualTo(PriceStatus.FRESH);
        assertThat(calendar.status(LocalDate.of(2026, 7, 23)))
                .isEqualTo(PriceStatus.STALE);
    }

    @Test
    void usCloseIsEvaluatedInCorrespondingBeijingTime() {
        var beforeUsClose = calendarAt("2026-07-28T20:10:00Z");
        var afterUsClose = calendarAt("2026-07-28T20:20:00Z");

        assertThat(beforeUsClose.latestExpectedTradingDay())
                .isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(afterUsClose.latestExpectedTradingDay())
                .isEqualTo(LocalDate.of(2026, 7, 28));
    }

    @Test
    void thanksgivingIsNotTreatedAsATradingDay() {
        var calendar = calendarAt("2026-11-27T01:00:00Z");

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
                                BEIJING,
                LocalTime.of(16, 15),
                Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")));
    }
}

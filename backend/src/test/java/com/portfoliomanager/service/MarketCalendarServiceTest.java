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
        void afterCloseOnTradingDayUsesSameDay() {
                var calendar = calendarAt("2026-07-27T20:20:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 27));
        }

        @Test
        void saturdayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-07-25T12:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 24));
        }

        @Test
        void sundayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-07-26T15:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 24));
        }

        @Test
        void observedIndependenceDayHolidayFallsBackToThursday() {
                var calendar = calendarAt("2026-07-06T15:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 2));
        }

        @Test
        void laborDayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-09-08T01:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 9, 4));
        }

        @Test
        void observedNewYearHolidayFallsBackToPriorYearTradingDay() {
                var calendar = calendarAt("2023-01-03T02:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2022, 12, 30));
        }

        @Test
        void juneteenthObservedHolidayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2022-06-21T01:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2022, 6, 17));
        }

        @Test
        void goodFridayFallsBackToThursday() {
                var calendar = calendarAt("2026-04-04T01:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 4, 2));
        }

        @Test
        void statusForLatestTradingDayIsFresh() {
                var calendar = calendarAt("2026-07-28T20:20:00Z");

                assertThat(calendar.status(LocalDate.of(2026, 7, 28)))
                                .isEqualTo(PriceStatus.FRESH);
        }

        @Test
        void statusForFutureDateRemainsFresh() {
                var calendar = calendarAt("2026-07-28T20:20:00Z");

                assertThat(calendar.status(LocalDate.of(2026, 7, 30)))
                                .isEqualTo(PriceStatus.FRESH);
        }

        @Test
        void statusForPreviousTradingDayIsStale() {
                var calendar = calendarAt("2026-07-28T20:20:00Z");

                assertThat(calendar.status(LocalDate.of(2026, 7, 27)))
                                .isEqualTo(PriceStatus.STALE);
        }

        @Test
        void beforeFridayCloseUsesThursday() {
                var calendar = calendarAt("2026-07-24T20:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 23));
        }

        @Test
        void afterFridayCloseUsesFriday() {
                var calendar = calendarAt("2026-07-24T20:30:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 24));
        }

        @Test
        void afterMondayCloseUsesMonday() {
                var calendar = calendarAt("2026-07-27T20:30:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 7, 27));
        }

        @Test
        void mlkDayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-01-20T02:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 1, 16));
        }

        @Test
        void presidentsDayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-02-17T02:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 2, 13));
        }

        @Test
        void memorialDayFallsBackToPreviousFriday() {
                var calendar = calendarAt("2026-05-26T01:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 5, 22));
        }

        @Test
        void christmasHolidayFallsBackToPreviousThursday() {
                var calendar = calendarAt("2026-12-26T02:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2026, 12, 24));
        }

        @Test
        void observedNextYearNewYearHolidayOnDec31FallsBackToDec30() {
                var calendar = calendarAt("2021-12-31T22:00:00Z");

                assertThat(calendar.latestExpectedTradingDay())
                                .isEqualTo(LocalDate.of(2021, 12, 30));
        }

        @Test
        void latestExpectedTradingDayNeverReturnsWeekend() {
                var saturday = calendarAt("2026-08-01T10:00:00Z");
                var sunday = calendarAt("2026-08-02T10:00:00Z");

                assertThat(saturday.latestExpectedTradingDay().getDayOfWeek().getValue())
                                .isBetween(1, 5);
                assertThat(sunday.latestExpectedTradingDay().getDayOfWeek().getValue())
                                .isBetween(1, 5);
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

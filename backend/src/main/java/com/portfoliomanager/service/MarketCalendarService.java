package com.portfoliomanager.service;

import com.portfoliomanager.domain.PriceStatus;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MarketCalendarService {

    private final ZoneId marketZone;
    private final LocalTime closeTime;
    private final Clock clock;

    @Autowired
    public MarketCalendarService(
            @Value("${market-data.time-zone:America/New_York}") String marketZone,
            @Value("${market-data.close-time:16:15}") String closeTime) {
        this(ZoneId.of(marketZone), LocalTime.parse(closeTime), Clock.systemUTC());
    }

    MarketCalendarService(ZoneId marketZone, LocalTime closeTime, Clock clock) {
        this.marketZone = marketZone;
        this.closeTime = closeTime;
        this.clock = clock;
    }

    public PriceStatus status(LocalDate priceDate) {
        if (priceDate == null) {
            return PriceStatus.UNAVAILABLE;
        }
        return priceDate.isBefore(latestExpectedTradingDay())
                ? PriceStatus.STALE
                : PriceStatus.FRESH;
    }

    LocalDate latestExpectedTradingDay() {
        ZonedDateTime marketNow = ZonedDateTime.now(clock).withZoneSameInstant(marketZone);
        LocalDate candidate = marketNow.toLocalDate();
        if (!isTradingDay(candidate)
                || (isTradingDay(candidate) && marketNow.toLocalTime().isBefore(closeTime))) {
            candidate = candidate.minusDays(1);
        }
        while (!isTradingDay(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    boolean isTradingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !isUsMarketHoliday(date);
    }

    private boolean isUsMarketHoliday(LocalDate date) {
        int year = date.getYear();
        return date.equals(observed(LocalDate.of(year, Month.JANUARY, 1)))
                || date.equals(observed(LocalDate.of(year + 1, Month.JANUARY, 1)))
                || date.equals(nthWeekday(year, Month.JANUARY, DayOfWeek.MONDAY, 3))
                || date.equals(nthWeekday(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3))
                || date.equals(easterSunday(year).minusDays(2))
                || date.equals(lastWeekday(year, Month.MAY, DayOfWeek.MONDAY))
                || (year >= 2022
                        && date.equals(observed(LocalDate.of(year, Month.JUNE, 19))))
                || date.equals(observed(LocalDate.of(year, Month.JULY, 4)))
                || date.equals(nthWeekday(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1))
                || date.equals(nthWeekday(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4))
                || date.equals(observed(LocalDate.of(year, Month.DECEMBER, 25)));
    }

    private LocalDate observed(LocalDate holiday) {
        return switch (holiday.getDayOfWeek()) {
            case SATURDAY -> holiday.minusDays(1);
            case SUNDAY -> holiday.plusDays(1);
            default -> holiday;
        };
    }

    private LocalDate nthWeekday(
            int year, Month month, DayOfWeek dayOfWeek, int ordinal) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek));
    }

    private LocalDate lastWeekday(int year, Month month, DayOfWeek dayOfWeek) {
        return LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.lastInMonth(dayOfWeek));
    }

    private LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}

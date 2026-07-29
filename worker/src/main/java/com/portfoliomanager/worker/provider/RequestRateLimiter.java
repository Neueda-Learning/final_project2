package com.portfoliomanager.worker.provider;

import java.time.Duration;

final class RequestRateLimiter {

    private final Object reservationLock = new Object();
    private final long intervalNanos;
    private long nextPermitNanos;

    private RequestRateLimiter(long intervalNanos) {
        this.intervalNanos = Math.max(0, intervalNanos);
    }

    static RequestRateLimiter perMinute(int requestsPerMinute) {
        if (requestsPerMinute <= 0) {
            return new RequestRateLimiter(0);
        }
        return new RequestRateLimiter(Duration.ofMinutes(1).toNanos() / requestsPerMinute);
    }

    static RequestRateLimiter fixedInterval(Duration interval) {
        return new RequestRateLimiter(interval.toNanos());
    }

    void acquire() {
        long waitNanos;
        synchronized (reservationLock) {
            long now = System.nanoTime();
            long reservedAt = Math.max(now, nextPermitNanos);
            nextPermitNanos = reservedAt + intervalNanos;
            waitNanos = reservedAt - now;
        }
        sleep(waitNanos);
    }

    static void sleep(Duration duration) {
        sleep(Math.max(0, duration.toNanos()));
    }

    private static void sleep(long waitNanos) {
        if (waitNanos <= 0) {
            return;
        }
        try {
            long millis = waitNanos / 1_000_000;
            int nanos = (int) (waitNanos % 1_000_000);
            Thread.sleep(millis, nanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataProviderException(
                    "Market-data request was interrupted", exception);
        }
    }
}

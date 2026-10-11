package org.springframework.samples.petclinic.service.visitIdempotency;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class VisitIdempotencyTestSupport {
    private VisitIdempotencyTestSupport() { }

    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for transaction boundary");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    public static long committedCount(org.springframework.transaction.PlatformTransactionManager manager,
            org.springframework.jdbc.core.JdbcTemplate jdbc, String table) {
        if (!java.util.Set.of("visits", "visit_creation_results").contains(table)) {
            throw new IllegalArgumentException("Unsupported test table");
        }
        var tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
    }

    public static void beforeCommit(Runnable action) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { action.run(); }
            });
    }

    public static final class MutableClock extends Clock {
        private final AtomicLong millis = new AtomicLong(Instant.parse("2026-10-11T12:00:00Z").toEpochMilli());
        public void set(long value) { millis.set(value); }
        public void advance(long delta) { millis.addAndGet(delta); }
        @Override public long millis() { return millis.get(); }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis()); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }
    }
}

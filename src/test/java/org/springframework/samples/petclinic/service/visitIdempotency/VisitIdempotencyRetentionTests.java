package org.springframework.samples.petclinic.service.visitIdempotency;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.VisitCreationResult;
import static org.assertj.core.api.Assertions.*;

public interface VisitIdempotencyRetentionTests {
    AbstractVisitIdempotencyTests fixture();
    @Test default void deadlineStartsAfterCommitAndNeverRenews() {
        var f=fixture(); var scope=f.scope(); var committed=new AtomicLong();
        f.hooks.beforeInsert=() -> f.clock.advance(1000);
        f.hooks.beforeFlush=() -> f.clock.advance(1000);
        f.hooks.beforeCommit=() -> f.clock.advance(5000);
        f.hooks.afterCommit=() -> { committed.set(f.clock.millis()); f.clock.advance(1); };
        f.hooks.beforeFinalize=() -> f.clock.advance(3000);
        var first=f.coordinator.create(scope,f.request()); f.hooks.reset();
        var stored=f.tx(() -> f.results.find(scope).orElseThrow());
        assertThat(stored.getCompletedAtMs()).isGreaterThan(committed.get());
        assertThat(stored.getExpiresAtMs()).isEqualTo(stored.getCompletedAtMs()+VisitCreationResult.RETENTION_MS);
        f.clock.set(stored.getExpiresAtMs()-1);
        assertThat(f.coordinator.create(scope,f.request()).responseBody()).isEqualTo(first.responseBody());
        assertThat(f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs()).isEqualTo(stored.getExpiresAtMs());
        assertThatThrownBy(() -> f.coordinator.create(scope,f.request().description("different"))).hasMessageContaining("different");
        f.clock.set(stored.getExpiresAtMs());
        var replacement=f.coordinator.create(scope,f.request());
        assertThat(replacement.getResponseVisitId()).isNotEqualTo(first.getResponseVisitId());
        var deadline=f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs();
        f.clock.set(deadline+1);
        assertThat(f.coordinator.create(scope,f.request()).getResponseVisitId()).isNotEqualTo(replacement.getResponseVisitId());
    }
    @Test default void pendingDeadlineSurvivesTimeAndOnlyOperatorCanFinalize() {
        var f=fixture(); var scope=f.scope();
        f.hooks.afterFinalize=() -> { throw new IllegalStateException("Finalizer rollback"); };
        var first=f.coordinator.create(scope,f.request()); f.hooks.reset();
        f.clock.advance(10*VisitCreationResult.RETENTION_MS);
        assertThat(f.coordinator.create(scope,f.request()).responseBody()).isEqualTo(first.responseBody());
        assertThatThrownBy(() -> f.coordinator.create(scope,f.request().description("different"))).isInstanceOf(org.springframework.samples.petclinic.rest.idempotency.VisitIdempotencyException.class);
        assertThat(f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs()).isNull();
        assertThat(f.tx(() -> f.results.finalizeDeadline(scope,first.getResponseVisitId()+1,f.clock.millis()))).isZero();
        assertThat(f.tx(() -> f.results.finalizeDeadline(scope,first.getResponseVisitId(),f.clock.millis()))).isOne();
        var deadline=f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs();
        assertThat(f.tx(() -> f.results.finalizeDeadline(scope,first.getResponseVisitId(),f.clock.millis()+1000))).isZero();
        assertThat(f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs()).isEqualTo(deadline);
        assertThat(f.coordinator.create(scope,f.request()).responseBody()).isEqualTo(first.responseBody());
    }
    @Test default void failedExpiredReplacementRestoresOriginalRowThenAllowsRetry() {
        var f=fixture(); var scope=f.scope(); var first=f.coordinator.create(scope,f.request());
        var deadline=f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs(); f.clock.set(deadline);
        long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
        f.hooks.afterInsert=() -> { throw new IllegalStateException("Replacement rollback"); };
        assertThatThrownBy(() -> f.coordinator.create(scope,f.request())).isInstanceOf(RuntimeException.class); f.hooks.reset();
        assertThat(f.tx(() -> f.results.find(scope).orElseThrow()).getResponseVisitId()).isEqualTo(first.getResponseVisitId());
        assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(before);
        assertThat(f.coordinator.create(scope,f.request()).getResponseVisitId()).isNotEqualTo(first.getResponseVisitId());
    }
    @Test default void nullableDatePreservesExistingDialectBehavior() {
        var f=fixture(); var scope=f.scope(); var body=f.request().date(null);
        long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
        if(f.getClass().getSimpleName().contains("H2")) {
            assertThatThrownBy(() -> f.coordinator.create(scope,body)).isInstanceOf(RuntimeException.class);
            assertThat(f.tx(() -> f.results.find(scope))).isEmpty();
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(before);
            assertThat(f.coordinator.create(scope,f.request()).getResponseDate()).isNotNull();
        } else {
            var first=f.coordinator.create(scope,body);
            assertThat(first.getResponseDate()).isNull();
            assertThat(f.coordinator.create(scope,body).responseBody()).isEqualTo(first.responseBody());
        }
    }
    @Test default void finalizerCommitFollowedByExceptionCannotRenewDeadline() {
        var f=fixture(); var scope=f.scope();
        f.hooks.afterFinalize=() -> org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override public void afterCommit() { throw new IllegalStateException("Uncertain finalizer outcome after commit"); }
            });
        var first=f.coordinator.create(scope,f.request()); f.hooks.reset();
        var established=f.tx(() -> f.results.find(scope).orElseThrow());
        assertThat(established.getExpiresAtMs()).isNotNull();
        f.clock.advance(1000);
        assertThat(f.coordinator.create(scope,f.request()).responseBody()).isEqualTo(first.responseBody());
        assertThat(f.tx(() -> f.results.finalizeDeadline(scope,first.getResponseVisitId(),f.clock.millis()))).isZero();
        assertThat(f.tx(() -> f.results.find(scope).orElseThrow()).getExpiresAtMs()).isEqualTo(established.getExpiresAtMs());
    }
}

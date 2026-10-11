package org.springframework.samples.petclinic.service.visitIdempotency;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.model.VisitCreationResult;
import static org.assertj.core.api.Assertions.*;

public interface VisitIdempotencyCleanupTests {
    AbstractVisitIdempotencyTests fixture();
    default int cleanup(int limit) {
        return fixture().context.getBean(org.springframework.samples.petclinic.service.VisitCreationResultCleanupService.class).cleanup(limit);
    }
    @Test default void cleanupIsBoundedAndNeverDeletesPendingSuccess() {
        var f=fixture(); var pending=f.scope();
        f.hooks.beforeFinalize=() -> { throw new IllegalStateException("Pending success"); };
        var original=f.coordinator.create(pending,f.request()); f.hooks.reset();
        for(int i=0;i<3;i++) f.coordinator.create(f.scope(),f.request());
        f.clock.advance(10*VisitCreationResult.RETENTION_MS);
        long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visit_creation_results");
        assertThat(cleanup(2)).isEqualTo(2);
        assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visit_creation_results")).isEqualTo(before-2);
        assertThat(f.coordinator.create(pending,f.request()).responseBody()).isEqualTo(original.responseBody());
        assertThat(f.tx(() -> f.results.find(pending).orElseThrow()).getExpiresAtMs()).isNull();
        assertThatThrownBy(() -> cleanup(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cleanup(1001)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test default void staleCleanupCandidateCannotDeleteReplacement() {
        var f=fixture(); var scope=f.scope(); f.coordinator.create(scope,f.request());
        var old=f.tx(() -> f.results.find(scope).orElseThrow()); f.clock.set(old.getExpiresAtMs());
        assertThat(f.tx(() -> f.results.findExpired(f.clock.millis(),1000))).anyMatch(r -> r.getScope().equals(scope));
        var replacement=f.coordinator.create(scope,f.request());
        assertThat(f.tx(() -> f.results.deleteExpired(scope,old.getExpiresAtMs(),f.clock.millis()))).isZero();
        assertThat(f.coordinator.create(scope,f.request()).responseBody()).isEqualTo(replacement.responseBody());
    }
}

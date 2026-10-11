package org.springframework.samples.petclinic.service.visitIdempotency;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public interface VisitIdempotencyRollbackTests {
    AbstractVisitIdempotencyTests fixture();
    @Test default void rejectedWritesFlushAndCommitLeaveReusableKey() {
        var f=fixture();
        for (String phase : new String[]{"afterVisit", "afterResult", "flush", "beforeCommit", "driverCommit", "checked"}) {
            var scope=f.scope();
            long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
            Runnable reject=() -> { throw new IllegalStateException("Injected " + phase); };
            switch (phase) {
                case "afterVisit" -> f.hooks.beforeInsert=reject;
                case "afterResult" -> f.hooks.afterInsert=reject;
                case "flush" -> {
                    var inserted=new AtomicBoolean();
                    f.hooks.afterInsert=() -> inserted.set(true);
                    f.hooks.beforeFlush=() -> { if (inserted.get()) reject.run(); };
                }
                case "beforeCommit" -> f.hooks.beforeCommit=reject;
                case "driverCommit" -> f.hooks.beforeCommit=() -> VisitIdempotencyCommitFaults.FAIL_COMMIT.set(true);
                case "checked" -> f.hooks.beforeInsert=() -> sneakyThrow(new Exception("Injected checked failure"));
                default -> throw new AssertionError();
            }
            try {
                assertThatThrownBy(() -> f.coordinator.create(scope,f.request())).isInstanceOf(Exception.class);
            } finally { f.hooks.reset(); VisitIdempotencyCommitFaults.FAIL_COMMIT.remove(); }
            assertThat(f.admission.isActive(scope)).as(phase).isFalse();
            assertThat(f.tx(() -> f.results.find(scope))).as(phase).isEmpty();
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).as(phase).isEqualTo(before);
            assertThat(f.coordinator.create(scope,f.request()).getResponseVisitId()).isNotNull();
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(before+1);
        }
    }
    @Test default void deadlineFailureCannotUndoCommittedCreation() {
        var f=fixture(); var scope=f.scope();
        long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
        f.hooks.beforeFinalize=() -> { throw new IllegalStateException("Injected finalization failure"); };
        var first=f.coordinator.create(scope,f.request());
        f.hooks.reset();
        var retained=f.tx(() -> f.results.find(scope).orElseThrow());
        assertThat(retained.getExpiresAtMs()).isNull();
        assertThat(f.admission.isActive(scope)).isFalse();
        assertThat(f.coordinator.create(scope,f.request()).getResponseVisitId()).isEqualTo(first.getResponseVisitId());
        assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(before+1);
    }
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable ex) throws E { throw (E)ex; }
}

package org.springframework.samples.petclinic.service.visitIdempotency;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.rest.idempotency.VisitIdempotencyException;
import static org.assertj.core.api.Assertions.*;

public interface VisitIdempotencyConcurrencyTests {
    AbstractVisitIdempotencyTests fixture();
    @Test default void overlapDuringCreationRejectsBeforeAnyResultAccess() throws Exception {
        var f=fixture(); var scope=f.scope(); var body=f.request();
        long visits=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
        long results=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visit_creation_results");
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        f.hooks.beforeInsert=() -> { entered.countDown(); VisitIdempotencyTestSupport.await(release); };
        try {
            var first=executor.submit(() -> f.coordinator.create(scope,body));
            VisitIdempotencyTestSupport.await(entered);
            for(var duplicate:new org.springframework.samples.petclinic.rest.dto.VisitFieldsDto[]{body,f.request().description("changed during creation")}) {
                var rejection=executor.submit(() -> {
                    f.hooks.forbidResultAccess.set(true);
                    try {
                        f.coordinator.create(scope,duplicate);
                        throw new AssertionError("Active duplicate unexpectedly succeeded");
                    } catch(VisitIdempotencyException ex) { return ex.getReason().status; }
                    finally { f.hooks.forbidResultAccess.remove(); }
                });
                assertThat(rejection.get(2,TimeUnit.SECONDS)).isEqualTo(409);
                assertThat(f.admission.isActive(scope)).isTrue();
                assertThat(first.isDone()).isFalse();
            }
            release.countDown(); var original=first.get(10,TimeUnit.SECONDS); f.hooks.reset();
            assertThat(f.admission.isActive(scope)).isFalse();
            assertThat(f.coordinator.create(scope,body).responseBody()).isEqualTo(original.responseBody());
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(visits+1);
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visit_creation_results")).isEqualTo(results+1);
        } finally { release.countDown(); f.hooks.reset(); executor.shutdownNow(); }
    }
    @Test default void overlapDuringRealCommitRejectsEitherBodyWithoutWaiting() throws Exception {
        var f=fixture(); var scope=f.scope(); var request=f.request();
        long before=VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits");
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var executor=Executors.newFixedThreadPool(2);
        f.hooks.beforeCommit=() -> { entered.countDown(); VisitIdempotencyTestSupport.await(release); };
        try {
            var first=executor.submit(() -> f.coordinator.create(scope,request));
            VisitIdempotencyTestSupport.await(entered);
            for (var duplicate : new org.springframework.samples.petclinic.rest.dto.VisitFieldsDto[]{request,
                    f.request().description("different valid body")}) {
                var rejected=executor.submit(() -> {
                    try { f.coordinator.create(scope,duplicate); return 0; }
                    catch (VisitIdempotencyException ex) { return ex.getReason().status; }
                });
                assertThat(rejected.get(2,TimeUnit.SECONDS)).isEqualTo(409);
            }
            assertThat(f.admission.isActive(scope)).isTrue();
            release.countDown();
            var original=first.get(10,TimeUnit.SECONDS);
            f.hooks.reset();
            assertThat(f.admission.isActive(scope)).isFalse();
            assertThat(f.coordinator.create(scope,request).getResponseVisitId()).isEqualTo(original.getResponseVisitId());
            assertThatThrownBy(() -> f.coordinator.create(scope,f.request().description("different valid body")))
                .isInstanceOfSatisfying(VisitIdempotencyException.class, ex -> assertThat(ex.getReason().status).isEqualTo(422));
            var one=executor.submit(() -> f.coordinator.create(scope,request));
            var two=executor.submit(() -> f.coordinator.create(scope,request));
            assertThat(one.get(5,TimeUnit.SECONDS).getResponseVisitId()).isEqualTo(original.getResponseVisitId());
            assertThat(two.get(5,TimeUnit.SECONDS).getResponseVisitId()).isEqualTo(original.getResponseVisitId());
            assertThat(VisitIdempotencyTestSupport.committedCount(f.manager,f.jdbc,"visits")).isEqualTo(before+1);
        } finally { release.countDown(); f.hooks.reset(); executor.shutdownNow(); }
    }
    @Test default void finalizerRetainsGateAndOtherScopesProgress() throws Exception {
        var f=fixture(); var scope=f.scope();
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        var executor=Executors.newSingleThreadExecutor();
        f.hooks.beforeFinalize=() -> { entered.countDown(); VisitIdempotencyTestSupport.await(release); };
        try {
            var first=executor.submit(() -> f.coordinator.create(scope,f.request()));
            VisitIdempotencyTestSupport.await(entered);
            assertThat(f.admission.isActive(scope)).isTrue();
            assertThatThrownBy(() -> f.coordinator.create(scope,f.request()))
                .isInstanceOfSatisfying(VisitIdempotencyException.class, ex -> assertThat(ex.getReason().status).isEqualTo(409));
            f.hooks.beforeFinalize=() -> { };
            assertThat(f.coordinator.create(f.scope(),f.request()).getResponseVisitId()).isNotNull();
            release.countDown(); first.get(10,TimeUnit.SECONDS);
            assertThat(f.admission.isActive(scope)).isFalse();
        } finally { release.countDown(); f.hooks.reset(); executor.shutdownNow(); }
    }
    @Test default void staleOwnerCannotReleaseNewAdmission() {
        var f=fixture(); var scope=f.scope();
        Object old=f.admission.tryClaim(scope);
        assertThat(f.admission.tryClaim(scope)).isNull();
        f.admission.release(scope,old);
        Object current=f.admission.tryClaim(scope);
        f.admission.release(scope,old);
        assertThat(f.admission.isActive(scope)).isTrue();
        f.admission.release(scope,current);
        assertThat(f.admission.isActive(scope)).isFalse();
    }
}

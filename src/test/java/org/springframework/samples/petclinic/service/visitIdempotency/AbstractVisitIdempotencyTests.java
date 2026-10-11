package org.springframework.samples.petclinic.service.visitIdempotency;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;
import org.springframework.samples.petclinic.rest.dto.*;
import static org.assertj.core.api.Assertions.*;

@org.springframework.context.annotation.Import(VisitIdempotencyTestConfiguration.class)
public abstract class AbstractVisitIdempotencyTests implements VisitIdempotencyConcurrencyTests, VisitIdempotencyRollbackTests, VisitIdempotencyRetentionTests, VisitIdempotencyCleanupTests {
    @Autowired protected org.springframework.samples.petclinic.service.VisitCreationCoordinator coordinator;
    @Autowired protected org.springframework.samples.petclinic.service.VisitCreationAdmission admission;
    @Autowired protected VisitIdempotencyTransactionHooks hooks;
    @Autowired protected VisitIdempotencyTestSupport.MutableClock clock;
    public AbstractVisitIdempotencyTests fixture() { return this; }
    protected VisitFieldsDto request() { return new VisitFieldsDto().date(LocalDate.of(2026,10,11)).description("matrix visit"); }
    @org.junit.jupiter.api.BeforeEach void resetTestState() {
        hooks.reset(); VisitIdempotencyCommitFaults.FAIL_COMMIT.remove(); clock.set(java.time.Instant.parse("2026-10-11T12:00:00Z").toEpochMilli());
    }
    @org.junit.jupiter.api.AfterEach void resetHooks() { hooks.reset(); }

    @Autowired protected org.springframework.context.ApplicationContext context;
    @Autowired protected VisitCreationResultRepository results;
    @Autowired protected PlatformTransactionManager manager;
    @Autowired protected JdbcTemplate jdbc;

    protected <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(manager).execute(status -> action.get());
    }
    protected VisitCreationScope scope() { return VisitCreationScope.of(1, 1, UUID.randomUUID().toString()); }
    protected VisitCreationResult sample(VisitCreationScope scope) {
        return VisitCreationResult.created(scope, new VisitFieldsDto().date(null).description("Exact Case "),
            new VisitDto().id(987).petId(1).date(null).description("Exact Case "));
    }
    @Autowired protected org.springframework.samples.petclinic.rest.controller.v1.OwnerRestControllerV1 ownerController;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles="OWNER_ADMIN")
    void committedCreationReplaysOriginalSnapshot() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(ownerController)
            .setControllerAdvice(new org.springframework.samples.petclinic.rest.advice.ExceptionControllerAdvice()).build();
        String key = UUID.randomUUID().toString();
        String body = "{\"date\":\"2026-10-11\",\"description\":\"shared replay\"}";
        long before = VisitIdempotencyTestSupport.committedCount(manager, jdbc, "visits");
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/owners/1/pets/1/visits")
            .header("Idempotency-Key", key).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body);
        var first = mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andReturn().getResponse();
        var retry = mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andReturn().getResponse();
        assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(retry.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(VisitIdempotencyTestSupport.committedCount(manager, jdbc, "visits")).isEqualTo(before + 1);
    }

    @Test void committedSnapshotSurvivesLiveVisitEditAndDeletion() {
        var scope=scope(); var body=request();
        long visits=VisitIdempotencyTestSupport.committedCount(manager,jdbc,"visits");
        long records=VisitIdempotencyTestSupport.committedCount(manager,jdbc,"visit_creation_results");
        var first=coordinator.create(scope,body);
        var original=tx(() -> results.find(scope).orElseThrow());
        var expected=new VisitDto().id(first.getResponseVisitId()).petId(1)
            .date(LocalDate.of(2026,10,11)).description("matrix visit");
        assertThat(original.responseBody()).isEqualTo(expected);
        assertThat(original.getCompletedAtMs()).isNotNull();
        assertThat(original.getExpiresAtMs()).isNotNull();
        int id=first.getResponseVisitId();
        assertThat(tx(() -> jdbc.update("UPDATE visits SET pet_id=?,visit_date=?,description=? WHERE id=?",
            2,java.sql.Date.valueOf("2026-10-12"),"edited live visit",id))).isEqualTo(1);
        for(boolean delete:new boolean[]{false,true}) {
            if(delete) assertThat(tx(() -> jdbc.update("DELETE FROM visits WHERE id=?",id))).isEqualTo(1);
            var replay=coordinator.create(scope,body);
            assertThat(replay.getResponseStatus()).isEqualTo(201);
            assertThat(replay.responseBody()).isEqualTo(expected);
            assertThat(replay.getResponseLocation()).isEqualTo("/api/visits/"+id);
            var persisted=tx(() -> results.find(scope).orElseThrow());
            assertThat(persisted.responseBody()).isEqualTo(expected);
            assertThat(persisted.getCompletedAtMs()).isEqualTo(original.getCompletedAtMs());
            assertThat(persisted.getExpiresAtMs()).isEqualTo(original.getExpiresAtMs());
            assertThat(persisted.getRequestDate()).isEqualTo(body.getDate());
            assertThat(persisted.getRequestDescription()).isEqualTo(body.getDescription());
            assertThat(VisitIdempotencyTestSupport.committedCount(manager,jdbc,"visits")).isEqualTo(visits+(delete?0:1));
            assertThat(VisitIdempotencyTestSupport.committedCount(manager,jdbc,"visit_creation_results")).isEqualTo(records+1);
        }
        assertThat(tx(() -> jdbc.queryForObject("SELECT COUNT(*) FROM visits WHERE id=?",Integer.class,id))).isZero();
    }

    @Test void resultRoundTripAndOneTimeDeadline() {
        var scope = scope();
        try {
            tx(() -> { results.insert(sample(scope)); results.flush(); return null; });
            var stored = tx(() -> results.find(scope).orElseThrow());
            assertThat(stored.getScope()).isEqualTo(scope);
            assertThat(stored.getRequestDate()).isNull();
            assertThat(stored.getResponseDate()).isNull();
            assertThat(stored.getResponseDescription()).isEqualTo("Exact Case ");
            assertThat(stored.getResponseStatus()).isEqualTo(201);
            assertThat(stored.getResponseLocation()).isEqualTo("/api/visits/987");
            assertThat(stored.isRetained(Long.MAX_VALUE)).isTrue();
            assertThat(stored.matches(new VisitFieldsDto().description("Exact Case "))).isTrue();
            assertThat(stored.matches(new VisitFieldsDto().description("exact case "))).isFalse();
            assertThat(tx(() -> results.finalizeDeadline(scope, 987, 100L))).isEqualTo(1);
            assertThat(tx(() -> results.finalizeDeadline(scope, 987, 200L))).isZero();
            stored = tx(() -> results.find(scope).orElseThrow());
            assertThat(stored.getCompletedAtMs()).isEqualTo(100);
            assertThat(stored.getExpiresAtMs()).isEqualTo(100 + VisitCreationResult.RETENTION_MS);
            assertThat(stored.isRetained(stored.getExpiresAtMs())).isFalse();
            assertThat(tx(() -> results.deleteExpired(scope, storedExpiry(), Long.MAX_VALUE))).isEqualTo(1);
        } finally { jdbc.update("DELETE FROM visit_creation_results WHERE key_hex=?", scope.getKeyHex()); }
    }
    private long storedExpiry() { return 100 + VisitCreationResult.RETENTION_MS; }

    @Test void scopeCaseAndConditionalCleanup() {
        String suffix = UUID.randomUUID().toString();
        var upper = VisitCreationScope.of(1, 1, "A" + suffix);
        var lower = VisitCreationScope.of(1, 1, "a" + suffix);
        assertThat(upper).isNotEqualTo(lower);
        assertThat(upper.hashCode()).isEqualTo(VisitCreationScope.of(1, 1, "A" + suffix).hashCode());
        try {
            tx(() -> { results.insert(sample(upper)); results.insert(sample(lower)); results.flush(); return null; });
            assertThat(tx(() -> results.findExpired(Long.MAX_VALUE, 1000))).noneMatch(r -> r.getScope().equals(upper));
            assertThat(tx(() -> results.deleteExpired(upper, 0, Long.MAX_VALUE))).isZero();
            tx(() -> results.finalizeDeadline(upper, 987, 100));
            assertThat(tx(() -> results.deleteExpired(upper, 101, Long.MAX_VALUE))).isZero();
            assertThat(tx(() -> results.find(lower))).isPresent();
            assertThatThrownBy(() -> tx(() -> { results.insert(sample(lower)); results.flush(); return null; })).isInstanceOf(RuntimeException.class);
            assertThat(tx(() -> results.find(lower))).isPresent();
        } finally {
            jdbc.update("DELETE FROM visit_creation_results WHERE key_hex IN (?,?)", upper.getKeyHex(), lower.getKeyHex());
        }
    }
    @Test void resultRollbackAndPairConstraint() {
        var scope = scope();
        assertThatThrownBy(() -> tx(() -> {
            results.insert(sample(scope)); results.flush(); throw new IllegalStateException("injected rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(tx(() -> results.find(scope))).isEmpty();
        try {
            tx(() -> { results.insert(sample(scope)); results.flush(); return null; });
            assertThatThrownBy(() -> jdbc.update("UPDATE visit_creation_results SET completed_at_ms=1 WHERE key_hex=?", scope.getKeyHex()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
            var unchanged = tx(() -> results.find(scope).orElseThrow());
            assertThat(unchanged.getCompletedAtMs()).isNull();
            assertThat(unchanged.getExpiresAtMs()).isNull();
        } finally { jdbc.update("DELETE FROM visit_creation_results WHERE key_hex=?", scope.getKeyHex()); }
    }
}

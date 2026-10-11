package org.springframework.samples.petclinic.rest.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=false", "spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:visit_conflicts;DB_CLOSE_DELAY=-1"})
@org.springframework.context.annotation.Import(org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTestConfiguration.class)
public class VisitIdempotencyConflictTests extends VisitIdempotencyReplayTests {
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTransactionHooks hooks;
    @org.junit.jupiter.api.BeforeEach void resetConflictHooks() { hooks.reset(); }
    @org.junit.jupiter.api.AfterEach void clearConflictHooks() { hooks.reset(); }

    @Test void activeCreationReturnsExactHttpProblemWithoutStorageAccess() throws Exception {
        String key=UUID.randomUUID().toString();
        long visits=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        hooks.beforeInsert=() -> {
            entered.countDown();
            org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTestSupport.await(release);
        };
        try {
            var first=executor.submit(() -> create(key,BODY));
            org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTestSupport.await(entered);
            for(String body:new String[]{BODY,BODY.replace("replay contract","different valid visit")}) {
                var rejection=executor.submit(() -> {
                    hooks.forbidResultAccess.set(true);
                    try {
                        return mvc.perform(post(PATH).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                            .andExpect(status().isConflict())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                            .andExpect(jsonPath("$.status").value(409))
                            .andExpect(jsonPath("$.type").value("urn:petclinic:problem:visit-creation-in-progress"))
                            .andExpect(jsonPath("$.title").value("Visit creation in progress"))
                            .andExpect(jsonPath("$.detail").value("Retry after the active creation attempt finishes."))
                            .andExpect(jsonPath("$.timestamp").isNotEmpty())
                            .andExpect(jsonPath("$.schemaValidationErrors").isEmpty())
                            .andExpect(header().doesNotExist("Idempotency-Replayed"))
                            .andExpect(header().doesNotExist("Retry-After")).andReturn();
                    } finally { hooks.forbidResultAccess.remove(); }
                });
                var problem=rejection.get(2,java.util.concurrent.TimeUnit.SECONDS).getResponse().getContentAsString();
                java.time.Instant.parse(new tools.jackson.databind.ObjectMapper().readTree(problem).get("timestamp").asText());
                assertThat(problem).doesNotContain(key,"replay contract","different valid visit");
                assertThat(first.isDone()).isFalse();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results);
            }
            release.countDown(); var original=first.get(10,java.util.concurrent.TimeUnit.SECONDS).getResponse(); hooks.reset();
            var replay=create(key,BODY).getResponse();
            assertThat(replay.getContentAsString()).isEqualTo(original.getContentAsString());
            assertThat(replay.getHeader("Location")).isEqualTo(original.getHeader("Location"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits+1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results+1);
        } finally { release.countDown(); hooks.reset(); executor.shutdownNow(); }
    }
    @Test void changedLogicalValuesRejectWithoutMutation() throws Exception {
        String key=UUID.randomUUID().toString();
        var first=create(key,BODY).getResponse();
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        for (String body : new String[]{BODY.replace("2026-10-11","2026-10-12"),
                BODY.replace("replay contract","Replay contract"), BODY.replace("replay contract","replay  contract")}) {
            mvc.perform(post(PATH).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:petclinic:problem:idempotency-request-mismatch"))
                .andExpect(jsonPath("$.status").value(422)).andExpect(jsonPath("$.schemaValidationErrors").isEmpty());
        }
        assertThat(create(key,BODY).getResponse().getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(before);
    }
    @Test void ownerPetScopesAndCaseDistinctOpaqueKeysAreIndependent() throws Exception {
        String key="A,"+UUID.randomUUID();
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        for (String path : new String[]{PATH,"/api/owners/2/pets/1/visits","/api/owners/1/pets/2/visits"}) {
            mvc.perform(post(path).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        }
        create("a"+key.substring(1),BODY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(before+4);
    }
}

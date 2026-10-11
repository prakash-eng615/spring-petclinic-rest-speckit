package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=false", "spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:visit_compat;DB_CLOSE_DELAY=-1"})
public class VisitIdempotencyCompatibilityTests extends VisitIdempotencyReplayTests {
    @Test void unkeyedRequestsRemainIndependentAndDoNotStoreResults() throws Exception {
        long visits = jdbc.queryForObject("SELECT COUNT(*) FROM visits", Long.class);
        long results = jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results", Long.class);
        for (int i=0; i<2; i++) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.date").value("2026-10-11"))
                .andExpect(jsonPath("$.description").value("replay contract"))
                .andExpect(jsonPath("$.petId").value(1)).andExpect(header().exists("Location"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits", Long.class)).isEqualTo(visits+2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results", Long.class)).isEqualTo(results);
    }
    @Test void existingValidationAndOwnerPetOutcomesRemainUnchanged() throws Exception {
        for (int owner : new int[]{999,2}) {
            mvc.perform(post("/api/owners/"+owner+"/pets/1/visits").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/owners/1/pets/99999/visits").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("MethodArgumentNotValidException"));
        mvc.perform(get("/api/owners/99999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/pets/1")).andExpect(status().isOk());
    }
}

package org.springframework.samples.petclinic.rest.controller;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"petclinic.security.enable=false", "spring.h2.console.enabled=false", "spring.datasource.url=jdbc:h2:mem:visit_mvc;DB_CLOSE_DELAY=-1"})
@ActiveProfiles({"h2", "spring-data-jpa"})
public class VisitIdempotencyReplayTests {
    static final String PATH = "/api/owners/1/pets/1/visits";
    static final String BODY = "{\"date\":\"2026-10-11\",\"description\":\"replay contract\"}";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).build(); }
    MvcResult create(String key, String body) throws Exception {
        return mvc.perform(post(PATH).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
    }
    @Test void reorderedJsonAndOtherHeadersRecoverOriginal() throws Exception {
        String key = UUID.randomUUID().toString();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM visits", Long.class);
        var first = create(key, BODY).getResponse();
        var firstBody = new ObjectMapper().readTree(first.getContentAsString());
        assertThat(firstBody.get("id").asInt()).isPositive();
        assertThat(firstBody.get("petId").asInt()).isEqualTo(1);
        assertThat(firstBody.get("date").asText()).isEqualTo("2026-10-11");
        assertThat(firstBody.get("description").asText()).isEqualTo("replay contract");
        assertThat(first.getHeader("Location")).isEqualTo("/api/visits/"+firstBody.get("id").asInt());
        var replay = mvc.perform(post(PATH).header("Idempotency-Key", key).header("X-Client-Attempt", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"description\": \"replay contract\", \"date\": \"2026-10-11\" }"))
            .andExpect(status().isCreated()).andExpect(header().doesNotExist("Idempotency-Replayed")).andReturn().getResponse();
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(replay.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits", Long.class)).isEqualTo(before + 1);
    }
    @Test void snapshotSurvivesLiveEditAndDeletion() throws Exception {
        String key = UUID.randomUUID().toString();
        var first = create(key, BODY).getResponse();
        int id = new ObjectMapper().readTree(first.getContentAsString()).get("id").asInt();
        jdbc.update("UPDATE visits SET description='edited' WHERE id=?", id);
        assertThat(create(key, BODY).getResponse().getContentAsString()).isEqualTo(first.getContentAsString());
        jdbc.update("DELETE FROM visits WHERE id=?", id);
        var replay = create(key, BODY).getResponse();
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(replay.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits WHERE id=?", Integer.class, id)).isZero();
    }
}

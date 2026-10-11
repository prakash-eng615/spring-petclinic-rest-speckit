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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=true", "spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:visit_security;DB_CLOSE_DELAY=-1"})
@ActiveProfiles({"h2", "spring-data-jpa"})
public class VisitIdempotencySecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @Test void filterAndRolesProtectFirstCreationAndReplay() throws Exception {
        String key=UUID.randomUUID().toString();
        String path=VisitIdempotencyReplayTests.PATH;
        String body=VisitIdempotencyReplayTests.BODY;
        var first=mvc.perform(post(path).with(user("owner").roles("OWNER_ADMIN"))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse();
        long visits=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
        int baseline=mvc.perform(post(path).with(user("wrong").roles("VET_ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        assertThat(baseline).isGreaterThanOrEqualTo(400);
        for (String attempt : new String[]{key, UUID.randomUUID().toString()}) {
            mvc.perform(post(path).header("Idempotency-Key",attempt).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(user("wrong").roles("VET_ADMIN"))
                    .header("Idempotency-Key",attempt).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(baseline));
        }
        var replay=mvc.perform(post(path).with(user("different-authorized-user").roles("OWNER_ADMIN"))
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse();
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(replay.getHeader("Location")).isEqualTo(first.getHeader("Location"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results);
    }
}

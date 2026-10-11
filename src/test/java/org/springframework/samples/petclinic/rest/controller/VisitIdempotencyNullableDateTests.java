package org.springframework.samples.petclinic.rest.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.VisitCreationScope;
import org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTestConfiguration;
import org.springframework.samples.petclinic.service.visitIdempotency.VisitIdempotencyTestSupport;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=false","spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:hsqldb:mem:visit_nullable_http"})
@ActiveProfiles(value={"hsqldb","spring-data-jpa"},inheritProfiles=false)
@Import(VisitIdempotencyTestConfiguration.class)
public class VisitIdempotencyNullableDateTests extends VisitIdempotencyReplayTests {
    private static final String OMITTED="{\"description\":\"nullable HTTP visit\"}";
    private static final String EXPLICIT_NULL="{\"date\":null,\"description\":\"nullable HTTP visit\"}";
    @Autowired VisitIdempotencyTestSupport.MutableClock clock;

    @Test void omittedAndExplicitNullReplayEquallyInEitherOrderWithoutRenewal() throws Exception {
        for(String[] bodies:new String[][]{{OMITTED,EXPLICIT_NULL},{EXPLICIT_NULL,OMITTED}}) {
            String key=UUID.randomUUID().toString();
            String keyHex=VisitCreationScope.of(1,1,key).getKeyHex();
            long visits=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
            long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
            var first=create(key,bodies[0]).getResponse();
            var tree=new tools.jackson.databind.ObjectMapper().readTree(first.getContentAsString());
            int id=tree.get("id").asInt();
            assertThat(id).isPositive();
            assertThat(tree.get("petId").asInt()).isEqualTo(1);
            assertThat(tree.get("description").asText()).isEqualTo("nullable HTTP visit");
            assertThat(tree.get("date")==null || tree.get("date").isNull()).isTrue();
            assertThat(first.getHeader("Location")).isEqualTo("/api/visits/"+id);
            Long anchor=jdbc.queryForObject("SELECT completed_at_ms FROM visit_creation_results WHERE key_hex=?",Long.class,keyHex);
            Long expiry=jdbc.queryForObject("SELECT expires_at_ms FROM visit_creation_results WHERE key_hex=?",Long.class,keyHex);
            assertThat(anchor).isNotNull(); assertThat(expiry).isEqualTo(anchor+86_400_000L);
            clock.advance(3_600_000L);
            var retry=create(key,bodies[1]).getResponse();
            assertThat(retry.getContentAsString()).isEqualTo(first.getContentAsString());
            assertThat(retry.getHeader("Location")).isEqualTo(first.getHeader("Location"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits+1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results+1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results WHERE key_hex=? AND request_date IS NULL AND response_date IS NULL",Integer.class,keyHex)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT visit_date FROM visits WHERE id=?",java.sql.Date.class,id)).isNull();
            assertThat(jdbc.queryForObject("SELECT completed_at_ms FROM visit_creation_results WHERE key_hex=?",Long.class,keyHex)).isEqualTo(anchor);
            assertThat(jdbc.queryForObject("SELECT expires_at_ms FROM visit_creation_results WHERE key_hex=?",Long.class,keyHex)).isEqualTo(expiry);
        }
    }
    @Test void unkeyedOmittedAndNullDatesKeepIndependentAcceptedBehavior() throws Exception {
        long visits=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
        for(String body:new String[]{OMITTED,EXPLICIT_NULL}) {
            var response=mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.petId").value(1)).andExpect(jsonPath("$.date").isEmpty())
                .andExpect(jsonPath("$.description").value("nullable HTTP visit")).andReturn().getResponse();
            int id=new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).get("id").asInt();
            assertThat(response.getHeader("Location")).isEqualTo("/api/visits/"+id);
            assertThat(jdbc.queryForObject("SELECT visit_date FROM visits WHERE id=?",java.sql.Date.class,id)).isNull();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits+2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results);
    }
}

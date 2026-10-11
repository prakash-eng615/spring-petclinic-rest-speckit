package org.springframework.samples.petclinic.rest.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.model.VisitCreationScope;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=false","spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:visit_h2_nullable_http;DB_CLOSE_DELAY=-1"})
public class VisitIdempotencyH2NullableDateTests extends VisitIdempotencyReplayTests {
    @Test void missingAndNullDatesPreserveH2RejectionWithoutConsumingKey() throws Exception {
        for(String body:new String[]{"{\"description\":\"nullable HTTP visit\"}","{\"date\":null,\"description\":\"nullable HTTP visit\"}"}) {
            String key=UUID.randomUUID().toString();
            String keyHex=VisitCreationScope.of(1,1,key).getKeyHex();
            long visits=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
            long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
            var legacy=mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andReturn().getResponse();
            var keyed=mvc.perform(post(PATH).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(legacy.getStatus())).andReturn().getResponse();
            assertThat(keyed.getContentType()).isEqualTo(legacy.getContentType());
            var mapper=new tools.jackson.databind.ObjectMapper();
            assertThat(mapper.readTree(keyed.getContentAsString()).get("title").asText())
                .isEqualTo(mapper.readTree(legacy.getContentAsString()).get("title").asText());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results WHERE key_hex=?",Integer.class,keyHex)).isZero();
            var corrected=create(key,BODY).getResponse();
            assertThat(create(key,BODY).getResponse().getContentAsString()).isEqualTo(corrected.getContentAsString());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(visits+1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results+1);
        }
    }
}

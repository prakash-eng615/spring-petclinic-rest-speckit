package org.springframework.samples.petclinic.rest.controller;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.samples.petclinic.rest.idempotency.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"petclinic.security.enable=false", "spring.h2.console.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:visit_validation;DB_CLOSE_DELAY=-1"})
public class VisitIdempotencyValidationTests extends VisitIdempotencyReplayTests {
    @Test void invalidKeysAndRepeatedHeadersRejectWithoutWrites() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class);
        long results=jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class);
        for (String key : new String[]{"", " ", " leading", "trailing ", "a\tb", "a\u001fb", "a\u007fb", "é", "x".repeat(129)}) {
            mvc.perform(post(PATH).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:petclinic:problem:invalid-idempotency-key"))
                .andExpect(jsonPath("$.title").value("Invalid idempotency key"))
                .andExpect(jsonPath("$.schemaValidationErrors").isEmpty());
        }
        for (String second : new String[]{"same","different"}) {
            mvc.perform(post(PATH).header("Idempotency-Key","same",second).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visits",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM visit_creation_results",Long.class)).isEqualTo(results);
    }
    @Test void validBoundariesAndCommaAreOpaque() throws Exception {
        for (String key : new String[]{"!", "!".repeat(128), "opaque,"+UUID.randomUUID()}) {
            var first=create(key,BODY).getResponse();
            assertThat(create(key,BODY).getResponse().getContentAsString()).isEqualTo(first.getContentAsString());
        }
    }
    @Test void validationAndMalformedBodyLeaveKeyReusable() throws Exception {
        for (String body : new String[]{"{\"description\":\"\"}", "{\"description\":\""+"x".repeat(256)+"\"}", "{"}) {
            String key=UUID.randomUUID().toString();
            var failure=mvc.perform(post(PATH).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
            assertThat(failure.getStatus()).isGreaterThanOrEqualTo(400);
            var first=create(key,BODY).getResponse();
            assertThat(create(key,BODY).getResponse().getContentAsString()).isEqualTo(first.getContentAsString());
        }
    }
    @Test void rawServletCardinalityDoesNotSplitCommaOrNormalize() {
        var validator=new IdempotencyKeyValidator();
        var request=new MockHttpServletRequest();
        assertThat(validator.validate(request)).isEmpty();
        request.addHeader("Idempotency-Key","a,b");
        assertThat(validator.validate(request)).contains("a,b");
        request.addHeader("Idempotency-Key","a,b");
        assertThatThrownBy(() -> validator.validate(request)).isInstanceOf(VisitIdempotencyException.class);
        for (String key : new String[]{"a\u0000b", "é", " ", " a", "a "}) {
            var invalid=new MockHttpServletRequest(); invalid.addHeader("Idempotency-Key",key);
            assertThatThrownBy(() -> validator.validate(invalid)).isInstanceOf(VisitIdempotencyException.class);
        }
    }
}

package org.springframework.samples.petclinic.service.visitIdempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={"spring.h2.console.enabled=false","logging.level.root=WARN","spring.jpa.show-sql=false"})
@ActiveProfiles(resolver=VisitIdempotencyProfilesResolver.class)
@EnabledIfEnvironmentVariable(named="POSTGRES_URL",matches=".+",disabledReason="Disposable postgres configuration unavailable; not a passing matrix result")
public class VisitIdempotencyPostgresTests extends AbstractVisitIdempotencyTests {
    @Test void restartPreservesFixedAndMissingDeadlines() { VisitIdempotencyRestartTestSupport.restartAndOperatorRecovery("postgres"); }
    @Test void repeatedFeatureSchemaPreservesResults() throws Exception {
        var scope=scope(); var original=coordinator.create(scope,request());
        VisitIdempotencyRestartTestSupport.repeatFeatureSchema(jdbc.getDataSource(),"postgres");
        org.assertj.core.api.Assertions.assertThat(coordinator.create(scope,request()).responseBody()).isEqualTo(original.responseBody());
    }
}

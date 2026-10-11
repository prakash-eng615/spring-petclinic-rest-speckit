package org.springframework.samples.petclinic.service.visitIdempotency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.h2.console.enabled=false", "logging.level.root=WARN", "spring.jpa.show-sql=false", "spring.datasource.url=jdbc:hsqldb:mem:visit_matrix_hsql"})
@ActiveProfiles(resolver = VisitIdempotencyProfilesResolver.class)
public class VisitIdempotencyHsqlTests extends AbstractVisitIdempotencyTests { }

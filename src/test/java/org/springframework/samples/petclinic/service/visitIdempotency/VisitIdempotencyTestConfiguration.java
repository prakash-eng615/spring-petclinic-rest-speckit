package org.springframework.samples.petclinic.service.visitIdempotency;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

@TestConfiguration
public class VisitIdempotencyTestConfiguration {
    @Bean public static VisitIdempotencyCommitFaults commitFaults() { return new VisitIdempotencyCommitFaults(); }
    @Bean @Primary
    public VisitIdempotencyTestSupport.MutableClock controlledClock() { return new VisitIdempotencyTestSupport.MutableClock(); }
    @Bean
    public VisitIdempotencyTransactionHooks transactionHooks() { return new VisitIdempotencyTransactionHooks(); }
}

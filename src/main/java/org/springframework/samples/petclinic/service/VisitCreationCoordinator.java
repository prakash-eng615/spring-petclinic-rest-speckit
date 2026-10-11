/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;
import org.springframework.samples.petclinic.rest.idempotency.VisitIdempotencyException;

@Service
public class VisitCreationCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(VisitCreationCoordinator.class);
    private final VisitCreationAdmission admission;
    private final VisitCreationResultReadService reads;
    private final VisitCreationTransactionService worker;
    private final VisitCreationResultFinalizationService finalizer;
    private final Clock clock;
    public VisitCreationCoordinator(VisitCreationAdmission admission, VisitCreationResultReadService reads,
            VisitCreationTransactionService worker, VisitCreationResultFinalizationService finalizer, Clock clock) {
        this.admission = admission; this.reads = reads; this.worker = worker; this.finalizer = finalizer; this.clock = clock;
    }
    @Transactional(propagation = Propagation.NEVER)
    public VisitCreationResult create(VisitCreationScope scope, VisitFieldsDto request) {
        for (;;) {
            if (admission.isActive(scope)) {
                throw new VisitIdempotencyException(VisitIdempotencyException.Reason.IN_PROGRESS);
            }
            var retained = reads.retained(scope);
            if (retained.isPresent()) {
                VisitCreationTransactionService.requireMatching(retained.get(), request);
                return retained.get();
            }
            Object token = admission.tryClaim(scope);
            if (token == null) { continue; }
            try {
                var outcome = worker.create(scope, request);
                if (outcome.created()) {
                    long anchor = clock.millis(); // worker proxy returned only after creation commit
                    try {
                        finalizer.finalizeDeadline(outcome.result(), anchor);
                    } catch (RuntimeException ex) {
                        // Do not log exception text/SQL: it may contain keys or clinic data.
                        LOG.error("Visit creation committed; deadline finalization failed. Operator recovery required. Error type={}",
                            ex.getClass().getSimpleName());
                    }
                }
                // Durable success is published before admission release, even with a NULL deadline.
                return outcome.result();
            } finally {
                admission.release(scope, token);
            }
        }
    }
}

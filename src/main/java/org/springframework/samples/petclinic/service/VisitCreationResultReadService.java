/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.service;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

@Service
public class VisitCreationResultReadService {
    private final VisitCreationResultRepository results;
    private final Clock clock;
    public VisitCreationResultReadService(VisitCreationResultRepository results, Clock clock) {
        this.results = results; this.clock = clock;
    }
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<VisitCreationResult> retained(VisitCreationScope scope) {
        return results.find(scope).filter(result -> result.isRetained(clock.millis()));
    }
}

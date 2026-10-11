/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.samples.petclinic.model.VisitCreationResult;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

@Service
public class VisitCreationResultFinalizationService {
    private final VisitCreationResultRepository results;
    public VisitCreationResultFinalizationService(VisitCreationResultRepository results) { this.results = results; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void finalizeDeadline(VisitCreationResult result, long anchor) {
        results.finalizeDeadline(result.getScope(), result.getResponseVisitId(), anchor);
        results.flush();
    }
}

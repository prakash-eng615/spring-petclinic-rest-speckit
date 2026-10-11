/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

@Service
public class VisitCreationResultCleanupService {
    private final VisitCreationResultRepository results;
    private final Clock clock;
    public VisitCreationResultCleanupService(VisitCreationResultRepository results, Clock clock) {
        this.results = results;
        this.clock = clock;
    }
    @Transactional
    public int cleanup(int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("Batch size must be 1–1000"); }
        long cutoff = clock.millis();
        int deleted = 0;
        for (var candidate : results.findExpired(cutoff, limit)) {
            deleted += results.deleteExpired(candidate.getScope(), candidate.getExpiresAtMs(), cutoff);
        }
        return deleted;
    }
}

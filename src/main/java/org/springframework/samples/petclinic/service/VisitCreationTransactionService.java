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
import org.springframework.samples.petclinic.mapper.VisitMapper;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;
import org.springframework.samples.petclinic.rest.idempotency.VisitIdempotencyException;

@Service
public class VisitCreationTransactionService {
    public record Outcome(VisitCreationResult result, boolean created) { }
    private final VisitCreationResultRepository results;
    private final ClinicService clinic;
    private final VisitMapper mapper;
    private final Clock clock;
    public VisitCreationTransactionService(VisitCreationResultRepository results, ClinicService clinic,
            VisitMapper mapper, Clock clock) {
        this.results = results; this.clinic = clinic; this.mapper = mapper; this.clock = clock;
    }
    @Transactional(rollbackFor = Exception.class)
    public Outcome create(VisitCreationScope scope, VisitFieldsDto request) {
        var existing = results.find(scope);
        long now = clock.millis();
        if (existing.isPresent()) {
            var result = existing.get();
            if (result.isRetained(now)) {
                requireMatching(result, request);
                return new Outcome(result, false);
            }
            results.deleteExpired(scope, result.getExpiresAtMs(), now);
        }
        var visit = mapper.toVisit(request);
        var pet = new Pet();
        pet.setId(scope.getPetId());
        visit.setPet(pet);
        clinic.saveVisit(visit);
        var response = mapper.toVisitDto(visit);
        results.flush();
        var result = VisitCreationResult.created(scope, request, response);
        results.insert(result);
        results.flush();
        return new Outcome(result, true);
    }
    static void requireMatching(VisitCreationResult result, VisitFieldsDto request) {
        if (!result.matches(request)) { throw new VisitIdempotencyException(VisitIdempotencyException.Reason.MISMATCH); }
    }
}

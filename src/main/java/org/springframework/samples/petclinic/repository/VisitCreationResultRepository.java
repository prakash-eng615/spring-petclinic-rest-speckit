/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.samples.petclinic.model.VisitCreationScope;
import org.springframework.samples.petclinic.model.VisitCreationResult;

public interface VisitCreationResultRepository {
    Optional<VisitCreationResult> find(VisitCreationScope scope);
    void insert(VisitCreationResult result);
    void flush();
    int deleteExpired(VisitCreationScope scope, long observedExpiry, long cutoff);
    List<VisitCreationResult> findExpired(long cutoff, int limit);
    int finalizeDeadline(VisitCreationScope scope, int originalVisitId, long anchor);
}

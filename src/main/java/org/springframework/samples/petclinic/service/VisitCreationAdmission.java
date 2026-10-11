/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.samples.petclinic.model.VisitCreationScope;

@Component
public class VisitCreationAdmission {
    private final ConcurrentHashMap<VisitCreationScope, Object> active = new ConcurrentHashMap<>();
    public boolean isActive(VisitCreationScope scope) { return active.containsKey(scope); }
    public Object tryClaim(VisitCreationScope scope) {
        Object token = new Object();
        return active.putIfAbsent(scope, token) == null ? token : null;
    }
    public void release(VisitCreationScope scope, Object token) { active.remove(scope, token); }
}

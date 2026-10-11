/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.rest.idempotency;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyValidator {
    public Optional<String> validate(HttpServletRequest request) {
        var headers = request.getHeaders("Idempotency-Key");
        if (!headers.hasMoreElements()) { return Optional.empty(); }
        String key = headers.nextElement();
        if (headers.hasMoreElements() || key == null || key.isEmpty() || key.length() > 128
                || key.chars().anyMatch(c -> c < 0x21 || c > 0x7e)) {
            throw new VisitIdempotencyException(VisitIdempotencyException.Reason.INVALID_KEY);
        }
        return Optional.of(key);
    }
}

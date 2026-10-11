/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.rest.idempotency;

public class VisitIdempotencyException extends RuntimeException {
    public enum Reason {
        INVALID_KEY(400, "invalid-idempotency-key", "Invalid idempotency key", "Provide exactly one key of 1–128 visible ASCII characters."),
        IN_PROGRESS(409, "visit-creation-in-progress", "Visit creation in progress", "Retry after the active creation attempt finishes."),
        MISMATCH(422, "idempotency-request-mismatch", "Idempotency request mismatch", "Use a new key for different visit values.");
        public final int status;
        public final String type;
        public final String title;
        public final String detail;
        Reason(int status, String type, String title, String detail) {
            this.status = status; this.type = "urn:petclinic:problem:" + type;
            this.title = title; this.detail = detail;
        }
    }
    private final Reason reason;
    public VisitIdempotencyException(Reason reason) { super(reason.detail); this.reason = reason; }
    public Reason getReason() { return reason; }
}

/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.model;

import java.time.LocalDate;
import java.util.Objects;
import jakarta.persistence.*;
import org.springframework.samples.petclinic.rest.dto.VisitDto;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;

@Entity
@Table(name = "visit_creation_results")
public class VisitCreationResult {
    public static final long RETENTION_MS = 86_400_000L;
    @EmbeddedId private VisitCreationScope scope;
    @Column(name = "request_date", nullable = true) private LocalDate requestDate;
    @Column(name = "request_description", nullable = false, length = 255) private String requestDescription;
    @Column(name = "response_visit_id", nullable = false) private Integer responseVisitId;
    @Column(name = "response_pet_id", nullable = false) private Integer responsePetId;
    @Column(name = "response_date", nullable = true) private LocalDate responseDate;
    @Column(name = "response_description", nullable = false, length = 255) private String responseDescription;
    @Column(name = "response_status", nullable = false) private Integer responseStatus;
    @Column(name = "response_location", nullable = false, length = 255) private String responseLocation;
    @Column(name = "completed_at_ms", nullable = true) private Long completedAtMs;
    @Column(name = "expires_at_ms", nullable = true) private Long expiresAtMs;
    protected VisitCreationResult() { }
    public VisitCreationResult(VisitCreationScope scope, LocalDate requestDate, String requestDescription, Integer responseVisitId, Integer responsePetId, LocalDate responseDate, String responseDescription, Integer responseStatus, String responseLocation, Long completedAtMs, Long expiresAtMs) {
        this.scope = scope;
        this.requestDate = requestDate;
        this.requestDescription = requestDescription;
        this.responseVisitId = responseVisitId;
        this.responsePetId = responsePetId;
        this.responseDate = responseDate;
        this.responseDescription = responseDescription;
        this.responseStatus = responseStatus;
        this.responseLocation = responseLocation;
        this.completedAtMs = completedAtMs;
        this.expiresAtMs = expiresAtMs;
    }
    public static VisitCreationResult created(VisitCreationScope scope, VisitFieldsDto request, VisitDto response) {
        return new VisitCreationResult(scope, request.getDate(), request.getDescription(),
            response.getId(), response.getPetId(), response.getDate(), response.getDescription(),
            201, "/api/visits/" + response.getId(), null, null);
    }
    public boolean isRetained(long now) { return expiresAtMs == null || now < expiresAtMs; }
    public boolean matches(VisitFieldsDto request) {
        return Objects.equals(requestDate, request.getDate())
            && Objects.equals(requestDescription, request.getDescription());
    }
    public VisitDto responseBody() {
        return new VisitDto().id(responseVisitId).petId(responsePetId)
            .date(responseDate).description(responseDescription);
    }
    public VisitCreationScope getScope() { return scope; }
    public LocalDate getRequestDate() { return requestDate; }
    public String getRequestDescription() { return requestDescription; }
    public Integer getResponseVisitId() { return responseVisitId; }
    public Integer getResponsePetId() { return responsePetId; }
    public LocalDate getResponseDate() { return responseDate; }
    public String getResponseDescription() { return responseDescription; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseLocation() { return responseLocation; }
    public Long getCompletedAtMs() { return completedAtMs; }
    public Long getExpiresAtMs() { return expiresAtMs; }
}

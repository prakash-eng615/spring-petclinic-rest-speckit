/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class VisitCreationScope implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(length = 32, nullable = false) private String operation;
    @Column(name = "owner_id", nullable = false) private Integer ownerId;
    @Column(name = "pet_id", nullable = false) private Integer petId;
    @Column(name = "key_hex", length = 256, nullable = false) private String keyHex;

    protected VisitCreationScope() { }
    public VisitCreationScope(String operation, Integer ownerId, Integer petId, String keyHex) {
        this.operation = Objects.requireNonNull(operation);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.petId = Objects.requireNonNull(petId);
        this.keyHex = Objects.requireNonNull(keyHex);
    }
    public static VisitCreationScope of(Integer ownerId, Integer petId, String key) {
        return new VisitCreationScope("addVisitToOwner", ownerId, petId,
            HexFormat.of().withUpperCase().formatHex(key.getBytes(StandardCharsets.US_ASCII)));
    }
    public String getOperation() { return operation; }
    public Integer getOwnerId() { return ownerId; }
    public Integer getPetId() { return petId; }
    public String getKeyHex() { return keyHex; }
    @Override public boolean equals(Object other) {
        return other instanceof VisitCreationScope s && Objects.equals(operation, s.operation)
            && Objects.equals(ownerId, s.ownerId) && Objects.equals(petId, s.petId)
            && Objects.equals(keyHex, s.keyHex);
    }
    @Override public int hashCode() { return Objects.hash(operation, ownerId, petId, keyHex); }
}

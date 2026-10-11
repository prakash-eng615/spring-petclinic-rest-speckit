/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.repository.jpa;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

public class VisitCreationResultJpaOperations {
    @PersistenceContext protected EntityManager em;
    public Optional<VisitCreationResult> find(VisitCreationScope scope) {
        return Optional.ofNullable(em.find(VisitCreationResult.class, scope));
    }
    public void insert(VisitCreationResult result) { em.persist(result); }
    public void flush() { em.flush(); }
    public int deleteExpired(VisitCreationScope scope, long observedExpiry, long cutoff) {
        int deleted = em.createQuery("DELETE FROM VisitCreationResult r WHERE r.scope=:scope AND r.expiresAtMs=:observed AND r.expiresAtMs<=:cutoff")
            .setParameter("scope", scope).setParameter("observed", observedExpiry).setParameter("cutoff", cutoff).executeUpdate();
        em.clear();
        return deleted;
    }
    public List<VisitCreationResult> findExpired(long cutoff, int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("Batch size must be 1–1000"); }
        return em.createQuery("SELECT r FROM VisitCreationResult r WHERE r.expiresAtMs<=:cutoff ORDER BY r.expiresAtMs", VisitCreationResult.class)
            .setParameter("cutoff", cutoff).setMaxResults(limit).getResultList();
    }
    public int finalizeDeadline(VisitCreationScope scope, int originalVisitId, long anchor) {
        int updated = em.createQuery("UPDATE VisitCreationResult r SET r.completedAtMs=:anchor, r.expiresAtMs=:expiry WHERE r.scope=:scope AND r.responseVisitId=:visitId AND r.completedAtMs IS NULL AND r.expiresAtMs IS NULL")
            .setParameter("anchor", anchor).setParameter("expiry", Math.addExact(anchor, VisitCreationResult.RETENTION_MS))
            .setParameter("scope", scope).setParameter("visitId", originalVisitId).executeUpdate();
        em.clear();
        return updated;
    }
}

/*
 * Copyright 2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 */
package org.springframework.samples.petclinic.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.repository.VisitCreationResultRepository;

@Repository
@Profile("jdbc")
public class JdbcVisitCreationResultRepositoryImpl implements VisitCreationResultRepository {
    private static final String SCOPE = "operation=:operation AND owner_id=:ownerId AND pet_id=:petId AND key_hex=:keyHex";
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcVisitCreationResultRepositoryImpl(DataSource source) { jdbc = new NamedParameterJdbcTemplate(source); }
    private MapSqlParameterSource parameters(VisitCreationScope scope) {
        return new MapSqlParameterSource("operation", scope.getOperation()).addValue("ownerId", scope.getOwnerId())
            .addValue("petId", scope.getPetId()).addValue("keyHex", scope.getKeyHex());
    }
    private VisitCreationResult map(ResultSet r, int row) throws SQLException {
        var scope = new VisitCreationScope(r.getString("operation"), r.getInt("owner_id"), r.getInt("pet_id"), r.getString("key_hex"));
        return new VisitCreationResult(scope, r.getObject("request_date", LocalDate.class), r.getString("request_description"),
            r.getInt("response_visit_id"), r.getInt("response_pet_id"), r.getObject("response_date", LocalDate.class),
            r.getString("response_description"), r.getInt("response_status"), r.getString("response_location"),
            r.getObject("completed_at_ms", Long.class), r.getObject("expires_at_ms", Long.class));
    }
    @Override public Optional<VisitCreationResult> find(VisitCreationScope scope) {
        return jdbc.query("SELECT * FROM visit_creation_results WHERE " + SCOPE, parameters(scope), this::map).stream().findFirst();
    }
    @Override public void insert(VisitCreationResult result) {
        var p = parameters(result.getScope());
        p.addValue("requestDate", result.getRequestDate(), java.sql.Types.DATE);
        p.addValue("requestDescription", result.getRequestDescription(), java.sql.Types.VARCHAR);
        p.addValue("responseVisitId", result.getResponseVisitId(), java.sql.Types.INTEGER);
        p.addValue("responsePetId", result.getResponsePetId(), java.sql.Types.INTEGER);
        p.addValue("responseDate", result.getResponseDate(), java.sql.Types.DATE);
        p.addValue("responseDescription", result.getResponseDescription(), java.sql.Types.VARCHAR);
        p.addValue("responseStatus", result.getResponseStatus(), java.sql.Types.INTEGER);
        p.addValue("responseLocation", result.getResponseLocation(), java.sql.Types.VARCHAR);
        p.addValue("completedAtMs", result.getCompletedAtMs(), java.sql.Types.BIGINT);
        p.addValue("expiresAtMs", result.getExpiresAtMs(), java.sql.Types.BIGINT);
        jdbc.update("INSERT INTO visit_creation_results (operation, owner_id, pet_id, key_hex, request_date, request_description, response_visit_id, response_pet_id, response_date, response_description, response_status, response_location, completed_at_ms, expires_at_ms) VALUES (:operation, :ownerId, :petId, :keyHex, :requestDate, :requestDescription, :responseVisitId, :responsePetId, :responseDate, :responseDescription, :responseStatus, :responseLocation, :completedAtMs, :expiresAtMs)", p);
    }
    @Override public void flush() { /* JDBC executes writes immediately on the transaction-bound connection. */ }
    @Override public int deleteExpired(VisitCreationScope scope, long observedExpiry, long cutoff) {
        return jdbc.update("DELETE FROM visit_creation_results WHERE " + SCOPE
            + " AND expires_at_ms=:observed AND expires_at_ms<=:cutoff",
            parameters(scope).addValue("observed", observedExpiry).addValue("cutoff", cutoff));
    }
    @Override public List<VisitCreationResult> findExpired(long cutoff, int limit) {
        if (limit < 1 || limit > 1000) { throw new IllegalArgumentException("Batch size must be 1–1000"); }
        // JDBC row limiting is shared by all retained database drivers.
        return jdbc.getJdbcTemplate().query(connection -> {
            var statement = connection.prepareStatement("SELECT * FROM visit_creation_results WHERE expires_at_ms<=? ORDER BY expires_at_ms");
            statement.setLong(1, cutoff);
            statement.setMaxRows(limit);
            return statement;
        }, this::map);
    }
    @Override public int finalizeDeadline(VisitCreationScope scope, int originalVisitId, long anchor) {
        return jdbc.update("UPDATE visit_creation_results SET completed_at_ms=:anchor, expires_at_ms=:expiry WHERE " + SCOPE
            + " AND response_visit_id=:visitId AND completed_at_ms IS NULL AND expires_at_ms IS NULL",
            parameters(scope).addValue("anchor", anchor).addValue("expiry", Math.addExact(anchor, VisitCreationResult.RETENTION_MS))
                .addValue("visitId", originalVisitId));
    }
}

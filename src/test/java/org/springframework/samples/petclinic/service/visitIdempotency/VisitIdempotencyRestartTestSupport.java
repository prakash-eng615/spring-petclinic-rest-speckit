package org.springframework.samples.petclinic.service.visitIdempotency;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.samples.petclinic.model.*;
import org.springframework.samples.petclinic.service.*;
import org.springframework.samples.petclinic.rest.dto.VisitFieldsDto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import static org.assertj.core.api.Assertions.*;

final class VisitIdempotencyRestartTestSupport {
    private VisitIdempotencyRestartTestSupport() { }
    static ConfigurableApplicationContext open(String database) {
        String prefix=database.equals("mysql") ? "MYSQL" : "POSTGRES";
        return new SpringApplicationBuilder(PetClinicApplication.class,VisitIdempotencyTestConfiguration.class)
            .profiles(database,System.getProperty("petclinic.test.repository","spring-data-jpa"))
            .run("--spring.profiles.active="+database+","+System.getProperty("petclinic.test.repository","spring-data-jpa"), "--spring.sql.init.mode=never", "--spring.jpa.hibernate.ddl-auto=none",
                "--spring.datasource.url="+System.getenv(prefix+"_URL"),
                "--spring.datasource.username="+System.getenv(prefix+"_USER"),
                "--spring.datasource.password="+System.getenv(prefix+"_PASS"),
                "--server.port=0", "--spring.h2.console.enabled=false", "--spring.jpa.show-sql=false",
                "--logging.level.root=WARN", "--petclinic.security.enable=false");
    }
    static void restartAndOperatorRecovery(String database) {
        var regular=VisitCreationScope.of(1,1,UUID.randomUUID().toString());
        var pending=VisitCreationScope.of(1,1,UUID.randomUUID().toString());
        var body=new VisitFieldsDto().date(java.time.LocalDate.of(2026,10,11)).description("restart contract");
        VisitCreationResult original, indefinite; long deadline;
        try(var first=open(database)) {
            var coordinator=first.getBean(VisitCreationCoordinator.class);
            original=coordinator.create(regular,body);
            var jdbc=new org.springframework.jdbc.core.JdbcTemplate(first.getBean(DataSource.class));
            deadline=jdbc.queryForObject("SELECT expires_at_ms FROM visit_creation_results WHERE key_hex=?",Long.class,regular.getKeyHex());
            first.getBean(VisitIdempotencyTransactionHooks.class).beforeFinalize=() -> {throw new IllegalStateException("Crash interval");};
            indefinite=coordinator.create(pending,body);
        }
        try(var restarted=open(database)) {
            var clock=restarted.getBean(VisitIdempotencyTestSupport.MutableClock.class);
            var coordinator=restarted.getBean(VisitCreationCoordinator.class);
            clock.set(deadline-1);
            var replay=coordinator.create(regular,body);
            assertThat(replay.responseBody()).isEqualTo(original.responseBody());
            assertThat(replay.getResponseStatus()).isEqualTo(201);
            assertThat(replay.getResponseLocation()).isEqualTo(original.getResponseLocation());
            assertThat(replay.getExpiresAtMs()).isEqualTo(deadline);
            clock.set(deadline);
            assertThat(coordinator.create(regular,body).getResponseVisitId()).isNotEqualTo(original.getResponseVisitId());
            clock.advance(10*VisitCreationResult.RETENTION_MS);
            assertThat(coordinator.create(pending,body).responseBody()).isEqualTo(indefinite.responseBody());
            assertThatThrownBy(() -> coordinator.create(pending,new VisitFieldsDto().description("changed")))
                .isInstanceOf(org.springframework.samples.petclinic.rest.idempotency.VisitIdempotencyException.class);
            // Quiescent test context; rehearse the runbook's exact conditional bound SQL.
            var jdbc=new NamedParameterJdbcTemplate(restarted.getBean(DataSource.class));
            var values=new MapSqlParameterSource().addValue("operation",pending.getOperation()).addValue("owner_id",pending.getOwnerId())
                .addValue("pet_id",pending.getPetId()).addValue("key_hex",pending.getKeyHex())
                .addValue("original_visit_id",indefinite.getResponseVisitId()).addValue("anchor_ms",clock.millis())
                .addValue("expiry_ms",clock.millis()+VisitCreationResult.RETENTION_MS);
            String sql="UPDATE visit_creation_results SET completed_at_ms=:anchor_ms, expires_at_ms=:expiry_ms WHERE operation=:operation AND owner_id=:owner_id AND pet_id=:pet_id AND key_hex=:key_hex AND response_visit_id=:original_visit_id AND completed_at_ms IS NULL AND expires_at_ms IS NULL";
            var tx=new org.springframework.transaction.support.TransactionTemplate(restarted.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            assertThat(tx.<Integer>execute(s -> jdbc.update(sql,values))).isEqualTo(1);
            values.addValue("anchor_ms",clock.millis()+1000).addValue("expiry_ms",clock.millis()+VisitCreationResult.RETENTION_MS+1000);
            assertThat(tx.<Integer>execute(s -> jdbc.update(sql,values))).isZero();
            var recovered=coordinator.create(pending,body);
            assertThat(recovered.responseBody()).isEqualTo(indefinite.responseBody());
            assertThat(recovered.getExpiresAtMs()-recovered.getCompletedAtMs()).isEqualTo(VisitCreationResult.RETENTION_MS);
            assertThat(recovered.getCompletedAtMs()).isEqualTo(clock.millis());
        }
    }
    static void repeatFeatureSchema(DataSource source,String database) throws Exception {
        String schema=new ClassPathResource("db/"+database+"/schema.sql").getContentAsString(StandardCharsets.UTF_8);
        String feature=schema.substring(schema.indexOf("-- Retained visit creation successes"));
        var populator=new ResourceDatabasePopulator(new ByteArrayResource(feature.getBytes(StandardCharsets.UTF_8)));
        populator.execute(source); populator.execute(source);
    }
}

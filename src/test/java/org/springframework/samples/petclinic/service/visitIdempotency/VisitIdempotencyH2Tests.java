package org.springframework.samples.petclinic.service.visitIdempotency;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"spring.h2.console.enabled=false", "logging.level.root=WARN", "spring.jpa.show-sql=false", "spring.datasource.url=jdbc:h2:mem:visit_matrix_h2;DB_CLOSE_DELAY=-1"})
@ActiveProfiles(resolver = VisitIdempotencyProfilesResolver.class)
public class VisitIdempotencyH2Tests extends AbstractVisitIdempotencyTests {
    @org.junit.jupiter.api.Test void inMemoryDatabaseBeginsNewLifetimeAfterClose() {
        String url="jdbc:h2:mem:lifetime_"+java.util.UUID.randomUUID()+";DB_CLOSE_DELAY=0";
        var scope=scope();
        try(var first=memoryContext(url)) {
            first.getBean(org.springframework.samples.petclinic.service.VisitCreationCoordinator.class).create(scope,request());
        }
        try(var second=memoryContext(url)) {
            var repository=second.getBean(org.springframework.samples.petclinic.repository.VisitCreationResultRepository.class);
            var transaction=new org.springframework.transaction.support.TransactionTemplate(second.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            java.util.Optional<?> retained=transaction.execute(s -> repository.find(scope));
            org.assertj.core.api.Assertions.assertThat(retained).isEmpty();
            second.getBean(org.springframework.samples.petclinic.service.VisitCreationCoordinator.class).create(scope,request());
        }
    }
    private org.springframework.context.ConfigurableApplicationContext memoryContext(String url) {
        return new org.springframework.boot.builder.SpringApplicationBuilder(org.springframework.samples.petclinic.PetClinicApplication.class)
            .profiles("h2",System.getProperty("petclinic.test.repository","spring-data-jpa"))
            .run("--spring.profiles.active=h2,"+System.getProperty("petclinic.test.repository","spring-data-jpa"), "--spring.datasource.url="+url,"--spring.sql.init.mode=always","--spring.jpa.hibernate.ddl-auto=none",
                "--server.port=0","--spring.h2.console.enabled=false","--petclinic.security.enable=false",
                "--spring.jpa.show-sql=false","--logging.level.root=WARN");
    }
}

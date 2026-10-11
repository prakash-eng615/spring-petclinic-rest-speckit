package org.springframework.samples.petclinic.service.visitIdempotency;

import java.util.Set;
import org.springframework.test.context.ActiveProfilesResolver;

public class VisitIdempotencyProfilesResolver implements ActiveProfilesResolver {
    @Override public String[] resolve(Class<?> testClass) {
        String adapter = System.getProperty("petclinic.test.repository", "spring-data-jpa");
        if (!Set.of("jdbc", "jpa", "spring-data-jpa").contains(adapter)) {
            throw new IllegalArgumentException("Unsupported test repository");
        }
        String name = testClass.getSimpleName();
        String database = name.contains("Hsql") ? "hsqldb" : name.contains("Mysql") ? "mysql"
            : name.contains("Postgres") ? "postgres" : "h2";
        return new String[] {database, adapter};
    }
}

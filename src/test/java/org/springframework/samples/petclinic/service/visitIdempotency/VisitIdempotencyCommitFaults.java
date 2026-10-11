package org.springframework.samples.petclinic.service.visitIdempotency;

import java.lang.reflect.*;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jdbc.datasource.DelegatingDataSource;

public class VisitIdempotencyCommitFaults implements BeanPostProcessor {
    static final ThreadLocal<Boolean> FAIL_COMMIT = ThreadLocal.withInitial(() -> false);
    @Override public Object postProcessAfterInitialization(Object bean, String name) {
        if (!(bean instanceof DataSource source)) { return bean; }
        return new DelegatingDataSource(source) {
            @Override public Connection getConnection() throws SQLException { return wrap(super.getConnection()); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                return wrap(super.getConnection(user,password));
            }
        };
    }
    private Connection wrap(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("commit") && FAIL_COMMIT.get()) {
                    FAIL_COMMIT.remove();
                    // A driver may reject commit. Roll back this deliberately rejected transaction
                    // before throwing, leaving deterministic committed-state evidence on every adapter.
                    connection.rollback();
                    throw new SQLException("Injected driver commit rejection", "40001");
                }
                try { return method.invoke(connection,args); }
                catch (InvocationTargetException ex) { throw ex.getCause(); }
            });
    }
}

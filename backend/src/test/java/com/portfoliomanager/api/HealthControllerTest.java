package com.portfoliomanager.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void livenessDoesNotDependOnDatabaseQuery() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:health");

        assertThat(new HealthController(dataSource).live().status()).isEqualTo("ok");
    }

    @Test
    void readinessReturnsMysqlCheckWhenQuerySucceeds() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ready;MODE=MySQL");

        var response = new HealthController(dataSource).ready();

        assertThat(response.status()).isEqualTo("ready");
        assertThat(response.checks()).containsEntry("mysql", "ok");
    }

    @Test
    void readinessThrowsWhenDatabaseQueryFails() {
        DataSource failingDataSource = new DataSource() {
            @Override
            public java.sql.Connection getConnection() throws SQLException {
                throw new SQLException("boom");
            }

            @Override
            public java.sql.Connection getConnection(String username, String password)
                    throws SQLException {
                throw new SQLException("boom");
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                throw new SQLException("Not a wrapper");
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {}

            @Override
            public void setLoginTimeout(int seconds) {}

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger()
                    throws SQLFeatureNotSupportedException {
                throw new SQLFeatureNotSupportedException();
            }
        };

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                        com.portfoliomanager.service.ServiceNotReadyException.class,
                        () -> new HealthController(failingDataSource).ready()).getMessage())
                .contains("MySQL readiness check failed");
    }
}

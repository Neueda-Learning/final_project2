package com.portfoliomanager.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void livenessDoesNotDependOnDatabaseQuery() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:health");

        assertThat(new HealthController(dataSource).live().status()).isEqualTo("ok");
    }
}

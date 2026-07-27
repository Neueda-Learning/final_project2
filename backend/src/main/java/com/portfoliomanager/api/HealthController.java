package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.HealthResponse;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @GetMapping("/live")
    public HealthResponse live() {
        return new HealthResponse("ok", "not-checked");
    }

    @GetMapping("/ready")
    public HealthResponse ready() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new HealthResponse("ok", "available");
        } catch (RuntimeException exception) {
            return new HealthResponse("degraded", "unavailable");
        }
    }
}

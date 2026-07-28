package com.portfoliomanager.api;

import com.portfoliomanager.api.ApiModels.HealthLiveResponse;
import com.portfoliomanager.api.ApiModels.HealthReadyResponse;
import com.portfoliomanager.service.ServiceNotReadyException;
import java.util.Map;
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
    public HealthLiveResponse live() {
        return new HealthLiveResponse("ok");
    }

    @GetMapping("/ready")
    public HealthReadyResponse ready() {
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return new HealthReadyResponse("ready", Map.of("mysql", "ok"));
        } catch (RuntimeException exception) {
            throw new ServiceNotReadyException("MySQL readiness check failed", exception);
        }
    }
}

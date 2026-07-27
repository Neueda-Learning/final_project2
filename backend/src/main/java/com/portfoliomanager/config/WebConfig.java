package com.portfoliomanager.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConfigurationProperties(prefix = "app")
public class WebConfig implements WebMvcConfigurer {

    private List<String> corsOrigins = List.of("http://localhost:5173");
    private String demoUserId = "11111111-1111-1111-1111-111111111111";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE");
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String getDemoUserId() {
        return demoUserId;
    }

    public void setDemoUserId(String demoUserId) {
        this.demoUserId = demoUserId;
    }
}

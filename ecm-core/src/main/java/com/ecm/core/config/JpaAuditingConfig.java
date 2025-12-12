package com.ecm.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }

            Object principal = authentication.getPrincipal();

            if (principal instanceof Jwt jwt) {
                // Extract preferred_username from Keycloak JWT
                String username = jwt.getClaimAsString("preferred_username");
                if (username != null) {
                    return Optional.of(username);
                }
                // Fallback to subject (sub claim)
                return Optional.ofNullable(jwt.getSubject());
            }

            if (principal instanceof String) {
                return Optional.of((String) principal);
            }

            return Optional.of("anonymous");
        };
    }
}

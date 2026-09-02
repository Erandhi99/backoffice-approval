package com.senfin.backoffice_approval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT (JSON Web Token) settings.
 *
 * <p>Maps the {@code app.jwt.*} properties from {@code application.yaml}
 * to a type-safe Java bean.
 *
 * <p>Relaxed binding converts the kebab-case key {@code expiration-ms}
 * to the camelCase field {@code expirationMs} automatically.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Secret key used to sign and verify JWT tokens. */
    private String secret;

    /** Token lifetime in milliseconds (default 24h). */
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
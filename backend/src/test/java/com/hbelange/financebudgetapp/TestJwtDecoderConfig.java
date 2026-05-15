package com.hbelange.financebudgetapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

@TestConfiguration
class TestJwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return token -> { throw new JwtException("JWT validation disabled in tests"); };
    }
}

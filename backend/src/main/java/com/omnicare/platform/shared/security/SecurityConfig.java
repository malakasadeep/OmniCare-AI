package com.omnicare.platform.shared.security;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The filter chain.
 *
 * <p>Stateless: no session is created, no session is consulted, and CSRF
 * protection is off because there is no cookie for a third-party page to ride.
 * Authentication comes from the {@code Authorization} header on every request.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                // 401 rather than a redirect to a login page: this is an API.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    JwtService jwtService(JwtProperties properties, Clock clock) {
        return new JwtService(properties, clock);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * BCrypt rather than a plain hash: it is deliberately slow and salts each
     * password itself, so a leaked table cannot be attacked with a rainbow table
     * and brute force costs real time per guess.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

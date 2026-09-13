package com.omnicare.platform.shared.security;

import com.omnicare.platform.shared.tenancy.TenantFilter;
import jakarta.servlet.DispatcherType;
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
 *
 * <p>The two custom filters are constructed here rather than declared as beans
 * on purpose. Boot auto-registers any {@code Filter} bean into the plain servlet
 * chain as well, which would run them a second time outside Spring Security and
 * before it has had a chance to establish — or clear — the security context.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        // Internal dispatches, not client requests. A controller
                        // that throws is forwarded to /error, and the filters —
                        // being OncePerRequestFilter — do not run again, so that
                        // dispatch arrives with an empty security context. Left
                        // to anyRequest().authenticated() it is denied, and every
                        // 404 or 500 reaches the client as a misleading 401.
                        // ASYNC is here for the same reason, and Day 8's SSE
                        // responses will depend on it.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                // 401 rather than a redirect to a login page: this is an API.
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Strictly after the JWT filter: it reads the tenant off the
                // principal that filter established.
                .addFilterAfter(new TenantFilter(), JwtAuthenticationFilter.class)
                .build();
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

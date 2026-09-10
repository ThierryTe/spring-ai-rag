package com.tewendelabs.airag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.tewendelabs.airag.repository.UserRepository;
import com.tewendelabs.airag.security.JwtAuthenticationFilter;
import com.tewendelabs.airag.security.JwtService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(
                    "Authentification geree via JWT, pas via UserDetailsService");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            UserRepository userRepository) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpStatus.FORBIDDEN.value());
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write(
                                    "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\",\"path\":\"" +
                                            req.getRequestURI() + "\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        // Seul /login n'exige pas de JWT : /me demande l'identite deja etablie par
                        // JwtAuthenticationFilter (voir AuthController.me).
                        .requestMatchers("/api/auth/login").permitAll()
                        // Mode demo (Phase 8) : identite geree applicativement via l'en-tete
                        // X-Demo-Session-Id (DemoSessionService), pas via Spring Security - meme
                        // principe que le JWT existant (RBAC applicatif, pas de roles Spring
                        // Security natifs).
                        .requestMatchers("/api/demo/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Documentation OpenAPI/Swagger (Phase 7) : a restreindre au niveau
                        // reverse-proxy en prod si le projet est deploye publiquement, comme
                        // /actuator/prometheus.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

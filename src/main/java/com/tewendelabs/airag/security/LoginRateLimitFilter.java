
package com.tewendelabs.airag.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> bucketsByIp =
            new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !"/api/auth/login".equals(
                request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain)
            throws ServletException, IOException {

        // Utiliser uniquement une adresse IP établie
        // par l'infrastructure de confiance.
        String ip = request.getRemoteAddr();

        Bucket bucket = bucketsByIp.computeIfAbsent(
                ip, key -> newBucket());

        if (!bucket.tryConsume(1)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Retry-After", "60");
            response.getWriter().write(
                    "{\"error\":\"TOO_MANY_REQUESTS\","
                            + "\"message\":\"Trop de tentatives de connexion. "
                            + "Veuillez reessayer plus tard.\"}");
            return;
        }

        chain.doFilter(request, response);
    }


    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(MAX_ATTEMPTS)
                        .refillGreedy(MAX_ATTEMPTS, WINDOW))
                .build();
    }

}

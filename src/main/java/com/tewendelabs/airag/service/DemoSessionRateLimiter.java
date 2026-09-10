package com.tewendelabs.airag.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.exceptions.DemoQuotaExceededException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * Limite le nombre de sessions demo creables par adresse IP : sans ca, {@code POST
 * /api/demo/sessions} (public, sans JWT) permet de contourner le quota de questions par session en
 * en recreant une a l'infini. Etat en memoire (pas de backend distribue) : suffisant pour un
 * deploiement mono-instance, se reinitialise a chaque redemarrage.
 */
@Component
public class DemoSessionRateLimiter {

    private final DemoSessionProperties properties;
    private final ConcurrentHashMap<String, Bucket> bucketsByIp = new ConcurrentHashMap<>();

    public DemoSessionRateLimiter(DemoSessionProperties properties) {
        this.properties = properties;
    }

    public void checkAndConsume(String ipAddress) {
        Bucket bucket = bucketsByIp.computeIfAbsent(ipAddress, ip -> newBucket());
        if (!bucket.tryConsume(1)) {
            throw new DemoQuotaExceededException(
                    "Trop de sessions demo creees depuis cette adresse, veuillez reessayer plus tard");
        }
    }

    private Bucket newBucket() {
        int limit = properties.maxSessionCreationsPerIpPerHour();
        Bandwidth bandwidth = Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofHours(1)));
        return Bucket.builder().addLimit(bandwidth).build();
    }
}

package com.tewendelabs.airag.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.tewendelabs.airag.config.DemoSessionProperties;
import com.tewendelabs.airag.exceptions.DemoQuotaExceededException;

class DemoSessionRateLimiterTest {

    private final DemoSessionProperties properties = new DemoSessionProperties(5, 2, 2, 2);
    private final DemoSessionRateLimiter rateLimiter = new DemoSessionRateLimiter(properties);

    @Test
    void allowsUpToConfiguredLimitPerIp() {
        assertThatCode(() -> rateLimiter.checkAndConsume("203.0.113.10")).doesNotThrowAnyException();
        assertThatCode(() -> rateLimiter.checkAndConsume("203.0.113.10")).doesNotThrowAnyException();
    }

    @Test
    void rejectsBeyondConfiguredLimitPerIp() {
        rateLimiter.checkAndConsume("203.0.113.20");
        rateLimiter.checkAndConsume("203.0.113.20");

        assertThatThrownBy(() -> rateLimiter.checkAndConsume("203.0.113.20"))
                .isInstanceOf(DemoQuotaExceededException.class);
    }

    @Test
    void tracksEachIpIndependently() {
        rateLimiter.checkAndConsume("203.0.113.30");
        rateLimiter.checkAndConsume("203.0.113.30");

        assertThatCode(() -> rateLimiter.checkAndConsume("203.0.113.31")).doesNotThrowAnyException();
    }
}

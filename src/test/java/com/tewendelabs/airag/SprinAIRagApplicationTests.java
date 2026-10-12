package com.tewendelabs.airag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;

class SprinAIRagApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext.isActive()).isTrue();
    }

}

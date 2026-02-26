package org.testcharm.fore2e;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.testcharm.Waiting;

@Configuration
public class E2eBeans {

    @Bean
    @Primary
    @Profile("test")
    public Waiting mockWaiting(E2eApi e2eApi) {
        return seconds -> {
            try {
                e2eApi.sleepSeconds(seconds);
            } catch (Exception ignored) {
            }
        };
    }
}

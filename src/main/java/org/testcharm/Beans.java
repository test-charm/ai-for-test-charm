package org.testcharm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class Beans {

    @Bean
    public Waiting waiting() {
        return seconds -> {
            try {
                TimeUnit.SECONDS.sleep(seconds);
            } catch (InterruptedException e) {
                log.error("Waiting seconds %d failed".formatted(seconds), e);
            }
        };
    }
}

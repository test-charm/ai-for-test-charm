package org.testcharm.fore2e;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "e2e-api", url = "${mock-server.endpoint}")
@Profile("test")
public interface E2eApi {

    @GetMapping("/mock/sleep-seconds")
    void sleepSeconds(@RequestParam("seconds") long seconds);
}

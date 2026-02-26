package org.testcharm.e2e;

import com.github.leeonky.dal.Assertions;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = {CucumberConfiguration.class}, loader = SpringBootContextLoader.class)
@CucumberContextConfiguration
public class ApplicationSteps {

    @Value("${testcharm.dal.dumpinput:true}")
    private boolean dalDumpInput;

    @Before
    public void disableDALDump() {
        Assertions.dumpInput(dalDumpInput);
    }

}

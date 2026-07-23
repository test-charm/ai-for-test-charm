package org.testcharm.e2e;

import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcharm.cucumber.restful.RestfulStep;
import org.testcharm.cucumber.restful.extensions.PathVariableReplacement;
import org.testcharm.jfactory.JFactory;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

@SpringBootTest(classes = CucumberConfiguration.class)
@CucumberContextConfiguration
public class ApplicationSteps {

    @Autowired
    private RestfulStep restfulStep;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.db.url}")
    private String dbUrl;

    @Value("${app.db.username}")
    private String dbUsername;

    @Value("${app.db.password}")
    private String dbPassword;

    @Autowired
    private JFactory jFactory;

    @Before(order = 0)
    public void resetScenarioState() {
        CookieHandler.setDefault(new CookieManager());
        restfulStep.setBaseUrl(baseUrl);
        restfulStep.setJFactory(jFactory);
        PathVariableReplacement.reset();
        PathVariableReplacement.replacements.put("session-id", UUID.randomUUID().toString());
        PathVariableReplacement.replacements.put("message-id", UUID.randomUUID().toString());
        clearDatabase();
    }

    @Before("@api-login")
    public void apiLogin() {
        restfulStep.postForm("/login", """
                {
                    username: joseph
                    password: anything
                }
                """);
    }

    private void clearDatabase() {
        try (
                Connection connection = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
                Statement statement = connection.createStatement()
        ) {
            statement.execute("TRUNCATE TABLE feedbacks, elements, steps, threads, users CASCADE");
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear e2e database", e);
        }
    }

}

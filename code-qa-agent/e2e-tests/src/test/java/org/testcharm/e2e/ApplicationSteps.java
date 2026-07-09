package org.testcharm.e2e;

import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcharm.cucumber.restful.RestfulStep;
import org.testcharm.cucumber.restful.extensions.PathVariableReplacement;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootTest(classes = CucumberConfiguration.class)
@CucumberContextConfiguration
public class ApplicationSteps {

    private static final Pattern ENGINE_SID_PATTERN = Pattern.compile("^0\\{\"sid\":\"([^\"]+)\"");
    private static final Pattern THREAD_ID_PATTERN = Pattern.compile("\"thread_id\":\"([^\"]+)\"");

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

    private final Map<String, String> cookies = new LinkedHashMap<>();

    @Before(order = 0)
    public void resetScenarioState() {
        CookieHandler.setDefault(new CookieManager());
        restfulStep.setBaseUrl(baseUrl);
        cookies.clear();
        PathVariableReplacement.reset();
        PathVariableReplacement.replacements.put("session-id", UUID.randomUUID().toString());
        PathVariableReplacement.replacements.put("message-id", UUID.randomUUID().toString());
        clearDatabase();
    }

    @Before("@api-login")
    public void apiLogin() {
        restfulStep.post("/login", "application/x-www-form-urlencoded", "username=joseph&password=anything");
        captureResponseState();
    }

    @AfterStep
    public void afterStep() {
        captureResponseState();
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

    private void captureResponseState() {
        RestfulStep.Response response = currentResponse();
        if (response == null) {
            return;
        }
        captureCookies(response);
        applyRequestHeaders();
        captureDynamicVariables(response);
    }

    private RestfulStep.Response currentResponse() {
        try {
            Field field = RestfulStep.class.getDeclaredField("response");
            field.setAccessible(true);
            return (RestfulStep.Response) field.get(restfulStep);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access latest HTTP response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void captureCookies(RestfulStep.Response response) {
        Object setCookie = response.getHeaders().get("Set-Cookie");
        if (setCookie == null) {
            return;
        }

        if (setCookie instanceof Collection<?> values) {
            values.stream().map(String::valueOf).forEach(this::storeCookie);
        } else {
            storeCookie(String.valueOf(setCookie));
        }

    }

    private void storeCookie(String setCookie) {
        String cookiePair = setCookie.split(";", 2)[0];
        int separator = cookiePair.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String key = cookiePair.substring(0, separator);
        String value = cookiePair.substring(separator + 1);
        cookies.put(key, value);
    }

    private void applyRequestHeaders() {
        if (!cookies.isEmpty()) {
            restfulStep.header("Cookie", cookies.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("; ")));
        }
    }

    private void captureDynamicVariables(RestfulStep.Response response) {
        if (response.body == null) {
            return;
        }
        String body = new String(response.body, StandardCharsets.UTF_8);
        var engineSidMatcher = ENGINE_SID_PATTERN.matcher(body);
        if (engineSidMatcher.find()) {
            PathVariableReplacement.replacements.put("engine-sid", engineSidMatcher.group(1));
        }
        var threadIdMatcher = THREAD_ID_PATTERN.matcher(body);
        if (threadIdMatcher.find()) {
            PathVariableReplacement.replacements.put("thread-id", threadIdMatcher.group(1));
        }
    }
}

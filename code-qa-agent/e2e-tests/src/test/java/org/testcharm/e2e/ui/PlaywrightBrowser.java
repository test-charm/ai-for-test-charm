package org.testcharm.e2e.ui;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import io.cucumber.java.Scenario;
import io.cucumber.plugin.event.TestCase;
import jakarta.annotation.PreDestroy;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Slf4j
@Component
public class PlaywrightBrowser {
    private static final String UPLOAD_AND_DOWNLOAD_DEFAULT_FOLDER = "/tmp/f2c/";
    private final Function<String, Locator> finder;
    private Playwright playwright;
    private com.microsoft.playwright.Browser browser;
    private Page page = null;

    PlaywrightBrowser(Function<String, Locator> finder) {
        this.finder = finder;
    }

    public PlaywrightBrowser() {
        finder = selector -> getPage().locator(selector);
    }

    public void launchByUrl(String path) {
        getPage().navigate("http://dashboard.net:8000" + path);
    }

    public void inputTextByPlaceholder(String placeholder, String text) {
        await().ignoreExceptions().untilAsserted(() -> waitVisibleElement(("//*[@placeholder='%s']".formatted(placeholder))).fill(text));
    }

    public void selectTextByPlaceholder(String placeholder, String text) {
        await().ignoreExceptions().untilAsserted(() -> waitVisibleElement(String.format("//*[normalize-space(@placeholder)='%s']", placeholder)).click());
        await().ignoreExceptions().untilAsserted(() -> waitVisibleElement(String.format("//*[normalize-space(@value)='%s' or normalize-space(text())='%s']/ancestor::*[contains(@class, 'el-select-dropdown__item')]", text, text)).click());
    }

    public void clickByText(String text) {
        await().ignoreExceptions().untilAsserted(() -> {
            try {
                Locator locator = waitVisibleElement(String.format("//*[normalize-space(@value)='%s' or normalize-space(text())='%s']", text, text));
                locator.click();
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @SneakyThrows
    public void shouldHaveText(String text) {
        await().ignoreExceptions().untilAsserted(() -> assertThat(finder.apply("//*[contains(text(),'" + text + "')]").all().stream().anyMatch(Locator::isVisible)).isTrue());
    }

    public void close(Scenario scenario) {
        if (page != null) {
            page.close();
            if (scenario != null)
                saveVideoForFailedScenario(scenario);
            page = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    @PreDestroy
    public void destroy() {
        playwright.close();
    }

    public void shouldNotHaveCss(String css) {
        await().ignoreExceptions().untilAsserted(() -> assertThat(finder.apply("//*[contains(concat(' ', normalize-space(@class), ' '), ' " + css + " ')]").all().stream().anyMatch(Locator::isVisible)).isFalse());
    }

    public void tick(int seconds) {
//        getPage().clock().install();
        getPage().clock().fastForward(seconds * 1000L);
    }

    public void shouldHaveExactText(String text) {
        await().ignoreExceptions().untilAsserted(() -> assertThat(finder.apply("//*[text()='" + text + "']").all().stream().anyMatch(Locator::isVisible)).isTrue());
    }

    public void shouldNotHaveExactText(String text) {
        await().ignoreExceptions().untilAsserted(() -> assertThat(finder.apply("//*[text()='" + text + "']").all().stream().anyMatch(Locator::isVisible)).isFalse());
    }

    public void clickByTextOfRowContaining(String rowText, String text) {
        await().ignoreExceptions().untilAsserted(() -> {
            var locator = waitVisibleElement(String.format("//tr[td//text()[contains(.,'%s')]]//button[span//text()[contains(.,'%s')]]", rowText, text));
            locator.click();
        });
    }

    public void press(String key) {
        getPage().keyboard().press(key);
    }

    public String getInputValueByXPath(String xpath) {
        return getPage().inputValue(xpath);
    }

    public void shouldHaveSelectOptions(String placeholder, List<String> options) {
        await().ignoreExceptions().untilAsserted(() -> {
            var list = finder.apply("//*[normalize-space(@placeholder)='%s']/../..//li".formatted(placeholder)).all().stream()
                    .map(Locator::innerText).toList();
            assertThat(list).containsExactlyElementsOf(options);
        });
    }

    public void clickByCss(String css) {
        await().ignoreExceptions().untilAsserted(() -> {
            waitVisibleElement(css).click();
        });
    }

    public boolean isVisibleByXPath(String xpath) {
        return finder.apply(xpath).all().stream().anyMatch(Locator::isVisible);
    }

    public void selectMultipleTextByPlaceholder(String key, String... values) {
        await().ignoreExceptions().untilAsserted(() -> {
            var locator = waitVisibleElement(String.format("//*[normalize-space(@placeholder)='%s']", key));
            locator.click(new Locator.ClickOptions().setForce(true));
        });
        for (String value : values) {
            await().ignoreExceptions().untilAsserted(() -> waitVisibleElement(String.format("//*[normalize-space(@value)='%s' or normalize-space(text())='%s']/ancestor::*[contains(@class, 'el-select-dropdown__item')]", value, value)).click());
        }
    }

    public void forceClickByText(String text) {
        await().ignoreExceptions().untilAsserted(() -> {
            try {
                Locator locator = waitVisibleElement(String.format("//*[normalize-space(@value)='%s' or normalize-space(text())='%s']", text, text));
                locator.click(new Locator.ClickOptions().setForce(true));
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    public void refresh() {
        getPage().reload();
    }

    public void setCurrentTime(Instant currentTime) {
        getPage().clock().setFixedTime(currentTime.toEpochMilli());
    }

    public void shouldNotHaveText(String text) {
        await().ignoreExceptions().untilAsserted(() -> assertThat(finder.apply("//*[contains(text(),'" + text + "')]").all().stream().anyMatch(Locator::isVisible)).isFalse());
    }

    public String getPageSource() {
        return getPage().content();
    }

    public void inputTextByLabel(String label, String value) {
        Locator locator = waitVisibleElement(String.format("//label[normalize-space(text())='%s']/following-sibling::*//*[self::input or self::textarea]", label));
        clearAndInputText(locator, value);
    }

    public void selectTextByLabel(String label, String... text) {
        waitVisibleElement(String.format("(//label[normalize-space(text())='%s']/following-sibling::*//input)[1]", label)).click();
        for (String s : text)
            clickByText(s);
    }

    @SneakyThrows
    public void saveVideoForFailedTestCase(TestCase testCase) {
        if (page != null) {
            page.close();
            page.video().saveAs(Path.of("../../../dev-ops/videos/%s-%d.webm".formatted(testCase.getName(), Instant.now().toEpochMilli())));
        }
    }

    public Page getPage() {
        if (page == null) {
            String wsHost = "playwright.tool.net";
            int wsPort = 13000;
            createPlaywright();
            browser = playwright.chromium().connect("ws://" + wsHost + ":" + wsPort + "/", new BrowserType.ConnectOptions().setHeaders(Map.of("x-playwright-launch-options", ("{ \"headless\": false,     \"downloadsPath\": \"%s\" }").formatted(UPLOAD_AND_DOWNLOAD_DEFAULT_FOLDER))));
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setAcceptDownloads(true)
                    .setViewportSize(1920, 1080)
                    .setTimezoneId("Asia/Shanghai")
                    .setRecordVideoSize(1920, 1080)
                    .setRecordVideoDir(Paths.get("../../../dev-ops/videos"))
            );
            page = context.newPage();
            page.clock().install();
            page.onConsoleMessage(message -> {
                if (message.type().equals("error")) {
                    log.error("Console message: {}", message.text());
                } else if (message.type().equals("log")) {
                    log.info("Console message: {}", message.text());
                }
            });
            page.onRequestFailed(request -> {
                if (request.failure() != null) {
                    log.error("Request failed: {}", request.failure());
                    log.error("Request failed: {}", request.url());
                    log.error("Request failed: {}", request.method());
                    log.error("Request failed: {}", request.headers());
                    log.error("Request failed: {}", request.postData());
                    var response = request.response();
                    if (response != null && !response.ok()) {
                        log.error("Request failed response: {}", response.status());
                        log.error("Request failed response: {}", response.statusText());
                        log.error("Request failed response: {}", response.headers());
                        log.error("Request failed response: {}", response.text());
                    }
                }
            });
        }
        return page;
    }

    private void clearAndInputText(Locator locator, String value) {
        locator.fill("");
        locator.fill(value);
    }

    private void createPlaywright() {
        if (playwright == null) playwright = Playwright.create();
    }

    @SneakyThrows
    private void saveVideoForFailedScenario(Scenario scenario) {
        if (scenario.isFailed()) {
            getPage().video().saveAs(Path.of("../../../dev-ops/videos/%s-%d.webm".formatted(scenario.getName(), Instant.now().toEpochMilli())));
        }
    }

    private Locator waitAllElement(String locator, Predicate<Locator> filter) {
        return await().ignoreExceptions().until(() -> {
            var locators = finder.apply(locator).all().stream().filter(filter).toList();
            if (locators.size() != 1)
                throw new RuntimeException("Expect only one element but: [%s]".formatted(locators.stream().map(Locator::textContent).collect(Collectors.joining("\n"))));
            return locators.get(0);
        }, Objects::nonNull);
    }

    private Locator waitElement(String locator) {
        return waitAllElement(locator, element -> true);
    }

    private Locator waitVisibleElement(String locator) {
        return waitAllElement(locator, Locator::isVisible);
    }
}

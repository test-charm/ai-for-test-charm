package org.testcharm.e2e.ui;

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class PlaywrightBrowser {
    private static final String UPLOAD_AND_DOWNLOAD_DEFAULT_FOLDER = "/tmp/f2c/";
    private Playwright playwright;
    private com.microsoft.playwright.Browser browser;
    private Page page = null;

    public void launchByUrl(String path) {
        getPage().navigate("http://dashboard.net:8000" + path);
    }

    public void close() {
        if (page != null) {
            page.close();
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

    public Page getPage() {
        if (page == null) {
            if (playwright == null) playwright = Playwright.create();
            browser = playwright.chromium().connect("ws://playwright.tool.net:13000", new BrowserType.ConnectOptions().setHeaders(Map.of("x-playwright-launch-options", ("{ \"headless\": false,     \"downloadsPath\": \"%s\" }").formatted(UPLOAD_AND_DOWNLOAD_DEFAULT_FOLDER))));
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setAcceptDownloads(true)
                    .setViewportSize(1280, 960)
                    .setTimezoneId("Asia/Shanghai")
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

}

package org.testcharm.e2e.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcharm.e2e.ui.core.Element;
import org.testcharm.e2e.ui.core.LoginPage;
import org.testcharm.e2e.ui.core.MainPage;
import org.testcharm.jfactory.JFactory;
import org.testcharm.pf.By;
import org.testcharm.pf.PlaywrightPageFlow;

public class UISteps {

    @Autowired
    PlaywrightBrowser playwrightBrowser;

    @Autowired
    JFactory jFactory;

    private MainPage mainPage;

    @Before("@ui-login")
    public void uiLogin() {
        操作("""
        登录: {
          'Email address': joseph
          Password: anything
        }
        """);

        用户应该("""
        ::eventually: {
          ::this: /.*👋 我已准备好分析代码库，请问你想了解什么？.*/
        }
        """);
    }

    @After("@ui-login or @close-browser")
    public void closeBrowser(Scenario scenario) {
        playwrightBrowser.close(scenario);
    }

    @当("操作:")
    public void 操作(String expression) {
        playwrightBrowser.launchByUrl("");
        Page page = playwrightBrowser.getPage();
        Locator html = page.locator("html");
        PlaywrightPageFlow pageflow = PlaywrightPageFlow.builder().page(page).jFactory(jFactory).build();
        Element element = new Element(pageflow, html);
        element.setLocator(By.css("html"));
        new LoginPage(element).operateAll(expression);
        mainPage = new MainPage(element);
    }

    @那么("用户应该:")
    public void 用户应该(String expression) {
        mainPage.should(expression);
    }

    @那么("用户操作:")
    public void 用户操作(String expression) {
        mainPage.operateAll(expression);
    }
}

package org.testcharm.e2e.ui.core;

import org.testcharm.pf.ScopedCollector;

public class LoginPage extends BasePage {
    public LoginPage(Element element) {
        super(element);
    }

    public ScopedCollector 登录() {
        return new ScopedCollector() {
            @Override
            public void onExit() {
                fillInBy(Element::label, build());

                perform("caption[Sign In].click");
            }
        };
    }
}

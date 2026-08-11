package org.testcharm.e2e.ui.core;

import org.testcharm.dal.runtime.ProxyObject;
import org.testcharm.pf.AbstractPanel;
import org.testcharm.pf.PanelStack;
import org.testcharm.pf.Target;

public class MainPage extends BasePage implements ProxyObject {
    public final Menu menu;
    private final PanelStack<NavigationPage> workingSpace = new PanelStack<>();

    public MainPage(Element element) {
        super(element);
        var sideBar = locate("css[text-sidebar-foreground]");
        menu = new Menu(sideBar.list().isEmpty() ? element : sideBar.single());
    }

    @Override
    public Object getValue(Object o) {
        return switch ((String) o) {
            case "警告" -> locate("css[.el-message__content]").single();
            case "对话框" -> locate("css[.el-dialog]").filter(Element::isVisible).single();
            default -> menu.open((String) o);
        };
    }

    public class Menu extends AbstractPanel<Element> {

        public Menu(Element element) {
            super(element);
        }

        public NavigationPage open(String name) {
            return workingSpace.switchTo(new Target<>() {

                @Override
                public void navigateTo() {
                    switch (name) {
                        case "聊天" -> MainPage.this.performAll("""
                                id[new-chat-button].click
                                caption[Confirm].click
                                """);
                        default -> perform("caption[%s].click".formatted(name));
                    }
                }

                @Override
                public NavigationPage create() {
                    return switch (name) {
                        default ->
                                new ListPage(MainPage.this.locate("css[main]").single(), workingSpace, name, MainPage.this, name.replace("列表", ""));
                    };
                }

                @Override
                public boolean matches(NavigationPage page) {
                    return page.name().equals(name);
                }
            });
        }
    }
}

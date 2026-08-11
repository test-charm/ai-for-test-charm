package org.testcharm.e2e.ui.core;

import org.testcharm.dal.runtime.ProxyObject;
import org.testcharm.pf.PanelStack;
import org.testcharm.pf.PlaywrightElement;
import org.testcharm.pf.Target;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DetailPage extends BasePage implements NavigationPage, ProxyObject {
    protected final PanelStack<NavigationPage> workingSpace;
    private final MainPage mainPage;
    private final String name;
    private final Element detailCard;

    public DetailPage(Element element, String name, PanelStack<NavigationPage> workingSpace, MainPage mainPage) {
        super(element);
        this.name = name;
        this.workingSpace = workingSpace;
        this.mainPage = mainPage;

        detailCard = locate("""
                xpath["(//div[contains(concat(' ', @class, ' '), 'el-card')])"]""").list().getByIndex(0);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Object getValue(Object o) {
        Set<String> headers = detailItemKeys();
        List<Tabs> tabs = tabs();
        if (headers.contains((String) o)) {
            int index = new ArrayList<>(headers).indexOf((String) o);
            return detailCard.css(String.format(".el-card__body>div>div:nth-child(%d)>div:nth-child(2)", index + 1)).single();
        }
        return tabs.stream().filter(tab -> tab.getPropertyNames().contains(o))
                .findFirst()
                .map(t -> t.getValue(o))
                .orElseThrow(() -> new IllegalArgumentException(String.format("Detail item <%s> not exist", o)));
    }

    public List<Tabs> tabs() {
        return locate("css['.el-tabs']").list().values().map(Tabs::new).collect(Collectors.toList());
    }

    @Override
    public Set<?> getPropertyNames() {
        return ProxyObject.super.getPropertyNames();
    }

    private Set<String> detailItemKeys() {
        return detailCard.css(".el-card__body>div>div>div:first-child").list().values().map(PlaywrightElement::text)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    public class Tabs extends BasePage implements ProxyObject {
        private final PanelStack<NavigationPage> tabs = new PanelStack<>();

        public Tabs(Element element) {
            super(element);
        }

        @Override
        public Object getValue(Object o) {
            return tabs.switchTo(new Target<>() {
                @Override
                public void navigateTo() {
                    perform("css[.el-tabs__header].caption[%s].click".formatted(o));
                }

                @Override
                public NavigationPage create() {
                    int index = new ArrayList<>(getPropertyNames()).indexOf(o);
                    return new ListPage(locate("css[.el-tab-pane:nth-of-type(%s)]".formatted(index + 1)).single(), tabs, (String) o, mainPage, (String) o);
                }
            });
        }

        @Override
        public Set<?> getPropertyNames() {
            return element().css(".el-tabs__item").list().values().map(PlaywrightElement::text)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }
}

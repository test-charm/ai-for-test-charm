package org.testcharm.e2e.ui.core;

import org.testcharm.pf.AbstractPanel;

public abstract class BasePage extends AbstractPanel<Element> {
    public BasePage(Element element) {
        super(element);
    }

    public Element 对话框() {
        return locate("css[.el-dialog]").filter(Element::isVisible).single();
    }
}

package org.testcharm.e2e.ui.core;

import com.microsoft.playwright.Locator;
import org.testcharm.pf.Elements;
import org.testcharm.pf.PlaywrightElement;
import org.testcharm.pf.PlaywrightPageFlow;
import org.testcharm.util.CollectionHelper;

import java.util.*;

public class Element extends PlaywrightElement<Element, PlaywrightPageFlow> {
    private static final List<String> datePickers = Arrays.asList("今天");

    public Element(PlaywrightPageFlow pageFlow, Locator locator) {
        super(pageFlow, locator);
    }

    public Elements<Element> searchCheckbox(String label) {
        return locate("""
                css[input]::filter: { xpath[../..].text: '%s' @type: checkbox }
                """.formatted(label));
    }

    public Elements<Element> label(String label) {
        return locate("""
                caption[%s].xpath["ancestor::*[descendant::*[self::input or self::textarea]][1]//*[self::input or self::textarea]"]
                """.formatted(label));
    }

    public Elements<Element> searchFile(String buttonText) {
        return locate("""
                css[.el-upload__input]::filter: { xpath[../..].text: '%s' }
                """.formatted(buttonText));
    }

    @Override
    public Element fillIn(Object value) {
        if (isStartOfDatetimeRange() && datePickers.contains(value)) {
            click();
            root().perform("css[.el-picker-panel].caption[%s].click".formatted(value));
            return this;
        }
        if (isDropdown()) {
            CollectionHelper.asStream(value).forEach(each ->
                    locate("""
                            xpath[../..].css[.el-select-dropdown__item]::filter: {text: '%s'}
                            """.formatted(each)).single());

            perform("xpath[../..].click");

            //current element not available after click, need to find it again
            CollectionHelper.asStream(value).forEach(each ->
                    root().perform("""
                            (css[.el-select-dropdown__item]::filter: {visible: true, text: '%s'}).click
                            """.formatted(each)));
            return this;
        }
        if (checkAble()) {
            if (raw().isChecked() != (boolean) value)
                perform("xpath[..].click");
            return this;
        } else
            try {
                return super.fillIn(value);
            } finally {
                if (isEndOfDatetimeRange())
                    root().perform("css[.el-picker-panel .el-picker-panel__footer].caption[确定].forceClick");
            }
    }

    private Element root() {
        return new Element(pageFlow(), pageFlow().page().locator("html"));
    }

    private boolean isDropdown() {
        return classes().contains("el-input__inner") && xpath("../..").single().classes().contains("el-select");
    }

    private boolean isStartOfDatetimeRange() {
        return isOfDatetimeRange("startplaceholder");
    }

    private boolean isOfDatetimeRange(String placeholderAttributeName) {
        return classes().contains("el-range-input") && xpath("..").single().classes().contains("el-range-editor")
                && Objects.equals(xpath("..").single().attribute(placeholderAttributeName), attribute("placeholder"));
    }

    private boolean isEndOfDatetimeRange() {
        return isOfDatetimeRange("endplaceholder");
    }

    private Set<String> classes() {
        return new HashSet<>(Arrays.asList((String[]) attribute("class")));
    }

    public Element forceClick() {
        raw().click(new Locator.ClickOptions().setForce(true));
        return this;
    }

    public Elements<Element> id(String id) {
        return locate("""
                xpath["//*[@id='%s']"]
                """.formatted(id));
    }
}

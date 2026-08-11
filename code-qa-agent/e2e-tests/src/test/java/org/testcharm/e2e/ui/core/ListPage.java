package org.testcharm.e2e.ui.core;

import org.testcharm.dal.runtime.DALCollection;
import org.testcharm.dal.runtime.ProxyObject;
import org.testcharm.pf.*;
import org.testcharm.util.Zipped;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class ListPage extends BasePage implements Iterable, NavigationPage {
    private final PanelStack<NavigationPage> workingSpace;
    private final String name;
    protected final MainPage mainPage;
    private final String entityName;

    public ListPage(Element element, PanelStack<NavigationPage> workingSpace, String name, MainPage mainPage, String entityName) {
        super(element);
        this.workingSpace = workingSpace;
        this.name = name;
        this.mainPage = mainPage;
        this.entityName = entityName;
    }

    public ScopedJFactoryCollector 新建() {
        perform("caption[添加].click");
        return createForm().collector();
    }

    public ScopedJFactoryCollector 编辑(String rowIdentifier) {
        identifyRow(rowIdentifier).perform("caption[编辑].click");
        return createForm().collector();
    }

    public ListPage 操作(String rowIdentifier, String operation) {
        identifyRow(rowIdentifier).perform("caption[%s].click".formatted(operation));
        return this;
    }

    public HtmlTableRow identifyRow(String rowIdentifier) {
        return operate("""
                (::await: {
                    ::filter!: { ::values.text[]: [... $rowIdentifier ...] }
                })[0]
                """, ValuesOf.value("rowIdentifier", rowIdentifier));
    }

    public DetailPage 详情(String rowIdentifier) {
        String name = this.name + "/" + rowIdentifier;
        return workingSpace.switchTo(new Target<>() {
            @Override
            public void navigateTo() {
                identifyRow(rowIdentifier).perform("caption[详情].click");
                mainPage.locate("css[.app-main .el-card__header]!")
                        .filter(e -> e.text().contains("%s详情".formatted(entityName)))
                        .single();
            }

            @Override
            public DetailPage create() {
                return createDetailPage(mainPage.locate("css[.app-main]").single(), name, workingSpace);
            }

            @Override
            public boolean matches(NavigationPage current) {
                return name.equals(current.name());
            }
        });
    }

    public ScopedCollector 搜索() {
        return new ScopedCollector() {
            @Override
            public void onExit() {
                fillInConditions(build(), "css[.handy-search]");
                perform("caption[搜索].click");
            }
        };
    }

    public ScopedCollector 分页() {
        return new ScopedCollector() {
            @Override
            public void onExit() {
                fillInConditions(build(), "css[.el-pagination]");
            }
        };
    }

    public static class SearchForm extends BasePage {

        public SearchForm(Element listPage) {
            super(listPage);
        }

    }

    public ScopedCollector 排序() {
        return new ScopedCollector() {
            @Override
            public void onExit() {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) build()).entrySet()) {
                    perform("caption[%s]!.css[.%s].click".formatted(entry.getKey(), entry.getValue()));
                }
            }
        };
    }

    public void fillInConditions(Object build, String formLocator) {
        new SearchForm(element().locate(formLocator).single()).fillInBy((element, key) -> Elements.concat(
                Elements.concat(element.placeholder(key), element.searchCheckbox(key)),
                element.searchFile(key)), build);
    }

    protected DetailPage createDetailPage(Element element, String name, PanelStack<NavigationPage> workingSpace) {
        return new DetailPage(element, name, workingSpace, mainPage);
    }

    protected Form createForm() {
        return new Form(locate("css[.el-dialog]").filter(Element::isVisible).single(), Element::label, "确 定");
    }

    @Override
    public Iterator iterator() {
        Stream<HtmlTableRow> table1 = table(locate("css[.el-table__header-wrapper thead]"), locate("css[.el-table__body-wrapper tbody]"));
        if (locate("css[.el-table__fixed-header-wrapper thead]").list().isEmpty())
            return table1.iterator();
        Stream<HtmlTableRow> table2 = table(locate("css[.el-table__fixed-header-wrapper thead]"), locate("css[.el-table__fixed-body-wrapper tbody]"));
        return Zipped.zip(table1, table2).stream().map(pair -> new MultipleHtmlTableRow(pair.left(), pair.right())).iterator();
    }

    private Stream<HtmlTableRow> table(Elements<Element> headers, Elements<Element> body) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        headers.single().locate(".css[th]::filter: {visible: true}").list().stream().forEach(e -> columns.put(e.value().text(), e.index()));
        return body.single().locate(".css[tr]").list().values().map(e -> new HtmlTableRow(e, columns));
    }

    public ListPage 下一页() {
        perform("css[.btn-next].click");
        return this;
    }

    public ScopedJFactoryCollector 导入() {
        perform("caption[导入]!.click");
        return createForm().collector();
    }

    public ScopedCollector 删除() {
        return new ScopedCollector() {
            @Override
            public void onExit() {
                ((List<String>) build()).forEach(this::setValue);
            }

            @Override
            public void setValue(Object value) {
                perform("caption[删除].click");
                mainPage.locate("css[.el-dialog]").filter(org.testcharm.pf.Element::isVisible).single()
                        .perform("caption[确 定].click");
            }
        };
    }

    @Override
    public String name() {
        return name;
    }

    public static class HtmlTableRow extends BasePage implements ProxyObject {
        private final Map<String, Integer> columns;
        private final DALCollection<Element> cells;

        public HtmlTableRow(Element element, Map<String, Integer> columns) {
            super(element);
            this.columns = columns;
            cells = locate("css[td]::filter: {visible: true}").list();
        }

        @Override
        public Object getValue(Object column) {
            if ("css".equals(column)) {
                Set<String> classes = new LinkedHashSet<>(Arrays.asList((String[]) element().attribute("class")));
                locate("css[td span]").list().values().findFirst()
                        .ifPresent(span -> classes.addAll(Arrays.asList((String[]) span.attribute("class"))));
                return new ArrayList<>(classes);
            }
            return cells.getByIndex(columns.computeIfAbsent((String) column, ig -> {
                throw new IllegalArgumentException("Column <%s> not exist".formatted(column));
            }));
        }

        @Override
        public Set<?> getPropertyNames() {
            return columns.keySet();
        }
    }

    public static class Form extends BasePage {

        private final BiFunction<Element, String, Elements<Element>> by;
        private final String confirmButton;

        public Form(Element element, BiFunction<Element, String, Elements<Element>> by, String confirmButton) {
            super(element);
            this.by = by;
            this.confirmButton = confirmButton;
        }

        public ScopedJFactoryCollector collector() {
            return new ScopedJFactoryCollector(element().pageFlow().jFactory(), Object.class) {
                @Override
                public void onExit() {
                    fillInBy(by, build());
                    perform("caption['%s'].click".formatted(confirmButton));
                }
            };
        }

        public ScopedJFactoryCollector downloadCollector() {
            return new ScopedJFactoryCollector(element().pageFlow().jFactory(), Object.class) {
                @Override
                public void onExit() {
                    fillInBy(by, build());
                    perform("caption['%s'].download".formatted(confirmButton));
                }
            };
        }
    }

    public static class MultipleHtmlTableRow implements ProxyObject {
        private final HtmlTableRow main;
        private final HtmlTableRow fixed;

        public MultipleHtmlTableRow(HtmlTableRow first, HtmlTableRow second) {
            main = first;
            fixed = second;
        }

        @Override
        public Object getValue(Object property) {
            if ("css".equals(property))
                return main.getValue(property);
            if (main.columns.containsKey(property))
                return main.getValue(property);
            return fixed.getValue(property);
        }

        @Override
        public Set<?> getPropertyNames() {
            checkFormat();
            return new HashSet<String>() {{
                addAll(main.columns.keySet());
                addAll(fixed.columns.keySet());
            }};
        }

        private void checkFormat() {
            Set<String> duplicated = new HashSet<>(main.columns.keySet());
            if (duplicated.retainAll(fixed.columns.keySet()))
                throw new IllegalStateException("Duplicated columns: " + duplicated);
        }
    }
}

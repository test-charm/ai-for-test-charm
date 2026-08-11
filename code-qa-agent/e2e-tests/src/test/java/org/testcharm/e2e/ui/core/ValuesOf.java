package org.testcharm.e2e.ui.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

public class ValuesOf {
    public static <T> Values<T> value(Map<T, ?> values) {
        return new Values<T>().value(values);
    }

    public static <T> Values<T> value(T key, Object value) {
        return new Values<T>().value(key, value);
    }

    public static <T> Values<T> empty() {
        return new Values<>();
    }

    public static class Values<T> extends LinkedHashMap<T, Object> {
        public Values<T> value(T key, Object value) {
            put(key, value);
            return this;
        }

        public Values<T> value(Map<T, ?> values) {
            putAll(values);
            return this;
        }
    }

    public static class StringKeyValues extends Values<String> {
        public <T> T getValue(String key) {
            return (T) Stream.of(key.split("\\.")).map(s -> (Object) s).reduce(this, (o, k) -> ((Map) o).get(k));
        }
    }
}

package org.testcharm.cucumber.restful.extensions;

import java.util.HashMap;
import java.util.Map;

public class PathVariableReplacement {

    public static final Map<String, String> replacements = new HashMap<>();

    public static String eval(String expression) {
        if (!replacements.containsKey(expression)) {
            throw new IllegalArgumentException("No replacement for " + expression);
        }
        return replacements.get(expression);
    }

    public static void reset() {
        replacements.clear();
    }
}

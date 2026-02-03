package com.claySoftware.MathExpAssistant.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VariableBuilder {
    private VariableBuilder() {
    }

    public static Map<String, Double> buildVariables(List<Double> values) {
        Map<String, Double> vars = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            vars.put("X" + (i + 1), values.get(i));
        }
        return vars;
    }
}

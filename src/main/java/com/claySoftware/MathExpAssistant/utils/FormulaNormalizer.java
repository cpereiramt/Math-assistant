package com.claySoftware.MathExpAssistant.utils;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

public final class FormulaNormalizer {

    private FormulaNormalizer() {
    }

    public static String normalizeEquation(String equation) {
        return equation.trim().toUpperCase();
    }

    public static List<String> normalizeParameters(List<String> params) {
        return params.stream()
                .map(p -> p.trim().toUpperCase())
                .collect(Collectors.toList());
    }

    public static Map<String, Double> normalizeVariables(Map<String, Double> vars) {
        return vars.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().trim().toUpperCase(),
                        Map.Entry::getValue));
    }
}

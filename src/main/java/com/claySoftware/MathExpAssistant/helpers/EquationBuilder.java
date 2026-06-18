package com.claySoftware.MathExpAssistant.helpers;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class EquationBuilder {

    private static final String VALUES_PLACEHOLDER = "{VALUES}";

    private EquationBuilder() {
    }

    public static String buildVariadicEquation(String equationTemplate, int numberOfVariables) {
        if (equationTemplate == null || equationTemplate.isBlank()) {
            throw new IllegalArgumentException("Equation is required");
        }
        if (numberOfVariables < 1) {
            throw new IllegalArgumentException("At least one variable is required");
        }

        String variables = IntStream
                .rangeClosed(1, numberOfVariables)
                .mapToObj(i -> "X" + i)
                .collect(Collectors.joining(", "));

        return equationTemplate.replace(VALUES_PLACEHOLDER, variables);
    }
}

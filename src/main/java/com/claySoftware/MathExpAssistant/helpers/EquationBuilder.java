package com.claySoftware.MathExpAssistant.helpers;

public class EquationBuilder {

    public static String buildVariadicEquation(
            String operator,
            int numberOfVariables) {
        if (numberOfVariables < 1) {
            throw new IllegalArgumentException("At least one variable is required");
        }

        return String.join(
                " " + operator + " ",
                java.util.stream.IntStream
                        .rangeClosed(1, numberOfVariables)
                        .mapToObj(i -> "X" + i)
                        .toList());
    }
}

package com.claySoftware.MathExpAssistant.helpers;

import java.util.Map;

public class VariadicValidator {
    private VariadicValidator() {
    }

    public static int inferNFromVariables(Map<String, Double> variables) {
        // Espera X1..Xn. Descobre o maior índice.
        int max = 0;
        for (String k : variables.keySet()) {
            if (!k.matches("^X\\d+$")) {
                throw new IllegalArgumentException("Variadic variables must be named X1..Xn. Found: " + k);
            }
            int idx = Integer.parseInt(k.substring(1));
            max = Math.max(max, idx);
        }
        // Garantir que é contínuo: X1..Xmax
        for (int i = 1; i <= max; i++) {
            if (!variables.containsKey("X" + i)) {
                throw new IllegalArgumentException("Missing variadic variable: X" + i);
            }
        }
        return max;
    }
}

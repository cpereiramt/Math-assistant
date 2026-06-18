package com.claySoftware.MathExpAssistant.helpers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormulaExecutorTest {

    private final FormulaExecutor formulaExecutor = new FormulaExecutor();

    @Test
    void executesFixedFormulaWithVariables() {
        double result = formulaExecutor.executeFormula(
                "X + Y",
                Map.of("X", 10.0, "Y", 5.0));

        assertThat(result).isEqualTo(15.0);
    }

    @Test
    void executesVariadicFormulaBuiltWithIndexedVariables() {
        double result = formulaExecutor.executeFormula(
                "X1 + X2 + X3",
                Map.of("X1", 1.0, "X2", 2.0, "X3", 3.0));

        assertThat(result).isEqualTo(6.0);
    }

    @Test
    void executesVariadicFunctionSupportedByEvalEx() {
        double result = formulaExecutor.executeFormula(
                "SUM(X1, X2, X3)",
                Map.of("X1", 1.0, "X2", 2.0, "X3", 3.0));

        assertThat(result).isEqualTo(6.0);
    }

    @Test
    void executesMathFunctionsSupportedByEvalEx() {
        double result = formulaExecutor.executeFormula(
                "SQRT(X)",
                Map.of("X", 16.0));

        assertThat(result).isEqualTo(4.0);
    }
}

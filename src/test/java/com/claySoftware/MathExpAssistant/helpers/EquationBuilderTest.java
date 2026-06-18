package com.claySoftware.MathExpAssistant.helpers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EquationBuilderTest {

    @Test
    void expandsValuesPlaceholderForVariadicFormula() {
        String equation = EquationBuilder.buildVariadicEquation("SUM({VALUES})", 3);

        assertThat(equation).isEqualTo("SUM(X1, X2, X3)");
    }

    @Test
    void keepsEquationWithoutPlaceholder() {
        String equation = EquationBuilder.buildVariadicEquation("X1 + X2 + X3", 3);

        assertThat(equation).isEqualTo("X1 + X2 + X3");
    }
}

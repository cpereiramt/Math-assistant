package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation.Failure;
import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation.Quantity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.api.Assertions.assertTimeout;

class DecimalCalculationTest {
    @Test
    void preservesDecimalPrecisionAndRoundsHalfEven() {
        assertThat(value("0.1+0.2")).isEqualByComparingTo("0.3");
        assertThat(value("9007199254740993+1")).isEqualByComparingTo("9007199254740994");
        assertThat(value("1/3")).isEqualByComparingTo("0.3333333333333333333333333333333333");
        assertThat(value("ROUND(0.125,2)")).isEqualByComparingTo("0.12");
        assertThat(value("ROUND(0.135,2)")).isEqualByComparingTo("0.14");
        assertThat(value("1E300*1E-300")).isEqualByComparingTo("1");
    }

    @Test
    void rejectsUndefinedAndOutOfRangeResults() {
        assertThat(failure("1/(2-2)").getCode()).isEqualTo("division_by_zero");
        assertThat(failure("SQRT(-1)").getCode()).isEqualTo("non_finite");
        assertThat(failure("1E300*10").getCode()).isEqualTo("numeric_range");
        assertThat(failure("9^1000000").getCode()).isEqualTo("numeric_range");
        assertThatIllegalArgumentException().isThrownBy(() -> value("NaN"));
        assertThatIllegalArgumentException().isThrownBy(() -> value("Infinity"));
    }

    @Test
    void boundsExpressionWork() {
        assertThatIllegalArgumentException().isThrownBy(() -> value("1+".repeat(64) + "1"))
                .withMessageContaining("64 levels");
        assertThatIllegalArgumentException().isThrownBy(() -> value("1".repeat(4097)))
                .withMessageContaining("4096");
        assertTimeout(java.time.Duration.ofSeconds(1), () -> assertThat(value("1.01^100")).isPositive());
    }

    @Test
    void combinesDimensionsAndRequiresExplicitExchangeRate() {
        var variables = Map.of(
                "PRICE", new Quantity(new BigDecimal("80"), Map.of("CURRENCY_BRL", 1, "MASS", -1)),
                "WEIGHT", new Quantity(new BigDecimal("2.5"), Map.of("MASS", 1)),
                "CASH", new Quantity(new BigDecimal("20"), Map.of("CURRENCY_USD", 1)));
        Quantity total = DecimalCalculation.run("PRICE*WEIGHT+FX(CASH,5,\"BRL\")", variables, false);
        assertThat(total.value()).isEqualByComparingTo("300");
        assertThat(total.dimension()).containsExactlyEntriesOf(Map.of("CURRENCY_BRL", 1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> DecimalCalculation.run("PRICE*WEIGHT+CASH", variables, true));
    }

    private static BigDecimal value(String expression) {
        return DecimalCalculation.run(expression, Map.of(), false).value();
    }

    private static Failure failure(String expression) {
        return catchThrowableOfType(() -> value(expression), Failure.class);
    }
}

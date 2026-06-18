package com.claySoftware.MathExpAssistant.helpers;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.parser.ParseException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class FormulaExecutor {

    public FormulaExecutor() {
    }

    public double executeFormula(String equation, Map<String, Double> variables) {

        Expression expression = new Expression(equation);
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            expression.with(entry.getKey(), BigDecimal.valueOf(entry.getValue()));
        }

        try {
            return expression.evaluate().getNumberValue().doubleValue();
        } catch (EvaluationException | ParseException e) {
            throw new IllegalArgumentException("Invalid equation: " + e.getMessage(), e);
        }
    }
}

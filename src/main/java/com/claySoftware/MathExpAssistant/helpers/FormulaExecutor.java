package com.claySoftware.MathExpAssistant.helpers;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.parser.ParseException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Collection;
import java.util.TreeSet;

@Component
public class FormulaExecutor {

    public FormulaExecutor() {
    }

    public String validateSyntax(String equation) {
        return validateSyntax(equation, null);
    }

    public String validateSyntax(String equation, Collection<String> parameters) {
        try {
            Expression expression = new Expression(equation);
            expression.validate();
            if (parameters != null) {
                var undeclared = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                undeclared.addAll(expression.getUndefinedVariables());
                parameters.forEach(undeclared::remove);
                if (!undeclared.isEmpty()) return "undeclared variables: " + undeclared;
            }
            return null;
        } catch (ParseException ex) {
            return "invalid equation syntax: " + ex.getMessage();
        }
    }

    public double executeFormula(String equation, Map<String, Double> variables) {

        Expression expression = new Expression(equation);
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            expression.with(entry.getKey(), BigDecimal.valueOf(entry.getValue()));
        }

        try {
            double result = expression.evaluate().getNumberValue().doubleValue();
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("The result is outside the supported range");
            }
            return result;
        } catch (EvaluationException | ParseException e) {
            throw new IllegalArgumentException("Invalid equation: " + e.getMessage(), e);
        }
    }
}

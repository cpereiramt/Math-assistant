package com.claySoftware.MathExpAssistant.helpers;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FormulaExecutor {

    public FormulaExecutor() {
    }

    public double executeFormula(String equation, Map<String, Double> variables) {

        Expression expression = new ExpressionBuilder(equation)
                .variables(variables.keySet())
                .build();
        // passing the values of variables
        for (Map.Entry<String, Double> entry : variables.entrySet()) {
            expression.setVariable(entry.getKey(), entry.getValue());
        }

        return expression.evaluate();
    }
}

package com.claySoftware.MathExpAssistant.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class FormulaExpressionNode {
    private String id;
    private ExpressionNodeType type;
    private ExpressionOperator operator;
    private String variableName;
    private String constantValue;
    private String sourceFormulaId;
    private List<FormulaExpressionNode> children;
    private Map<String, FormulaExpressionNode> bindings;
}

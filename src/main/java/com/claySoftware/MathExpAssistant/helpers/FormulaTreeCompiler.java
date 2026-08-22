package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.ExpressionOperator;
import com.claySoftware.MathExpAssistant.models.FormulaCompilationResult;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormulaTreeCompiler {
    private static final int MAX_DEPTH = 30;
    private static final int MAX_NODES = 250;
    private static final int MAX_EQUATION_LENGTH = 10_000;

    private final FormulaRepository formulaRepository;

    public FormulaTreeCompiler(FormulaRepository formulaRepository) {
        this.formulaRepository = formulaRepository;
    }

    public FormulaCompilationResult compile(FormulaExpressionNode root) {
        CompilationContext context = new CompilationContext();
        String equation = compileNode(root, context, 0);
        if (equation.length() > MAX_EQUATION_LENGTH) {
            throw new IllegalArgumentException("compiled equation exceeds the maximum length");
        }

        return new FormulaCompilationResult(
                equation,
                equation,
                new ArrayList<>(context.parameters),
                new ArrayList<>(context.snapshots.values()));
    }

    private String compileNode(FormulaExpressionNode node, CompilationContext context, int depth) {
        if (node == null) {
            throw new IllegalArgumentException("expression node is required");
        }
        if (depth > MAX_DEPTH) {
            throw nodeError(node, "expression tree exceeds the maximum depth");
        }
        if (++context.nodeCount > MAX_NODES) {
            throw nodeError(node, "expression tree exceeds the maximum number of nodes");
        }
        if (node.getType() == null) {
            throw nodeError(node, "node type is required");
        }

        return switch (node.getType()) {
            case VARIABLE -> compileVariable(node, context);
            case CONSTANT -> compileConstant(node);
            case OPERATOR -> compileOperator(node, context, depth);
            case FORMULA -> compileFormula(node, context, depth);
        };
    }

    private String compileVariable(FormulaExpressionNode node, CompilationContext context) {
        String name = node.getVariableName();
        if (name == null || !name.trim().matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw nodeError(node, "invalid variable name");
        }
        name = name.trim().toUpperCase();
        context.parameters.add(name);
        return name;
    }

    private String compileConstant(FormulaExpressionNode node) {
        if (node.getConstantValue() == null || node.getConstantValue().isBlank()) {
            throw nodeError(node, "constant value is required");
        }
        try {
            return new BigDecimal(node.getConstantValue().trim()).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw nodeError(node, "invalid constant value");
        }
    }

    private String compileOperator(FormulaExpressionNode node, CompilationContext context, int depth) {
        ExpressionOperator operator = node.getOperator();
        if (operator == null) {
            throw nodeError(node, "operator is required");
        }
        List<FormulaExpressionNode> children = node.getChildren();
        int expected = operator == ExpressionOperator.NEGATE ? 1 : 2;
        if (children == null || children.size() != expected) {
            throw nodeError(node, "operator " + operator + " requires " + expected + " operand(s)");
        }

        String left = compileNode(children.get(0), context, depth + 1);
        if (operator == ExpressionOperator.NEGATE) {
            return "(-(" + left + "))";
        }
        String right = compileNode(children.get(1), context, depth + 1);
        String symbol = switch (operator) {
            case ADD -> "+";
            case SUBTRACT -> "-";
            case MULTIPLY -> "*";
            case DIVIDE -> "/";
            case POWER -> "^";
            case NEGATE -> throw new IllegalStateException("NEGATE handled as unary operator");
        };
        return "((" + left + ") " + symbol + " (" + right + "))";
    }

    private String compileFormula(FormulaExpressionNode node, CompilationContext context, int depth) {
        if (node.getSourceFormulaId() == null || node.getSourceFormulaId().isBlank()) {
            throw nodeError(node, "sourceFormulaId is required");
        }
        FormulaEntity source = formulaRepository
                .findByIdAndStatus(node.getSourceFormulaId(), FormulaStatus.PUBLIC)
                .orElseThrow(() -> nodeError(node, "public source formula was not found"));
        if (source.isVariable()) {
            throw nodeError(node, "variadic formulas are not supported in the builder yet");
        }
        if (source.getEquation() == null || source.getParameters() == null) {
            throw nodeError(node, "source formula is incomplete");
        }

        Map<String, FormulaExpressionNode> bindings = node.getBindings();
        if (bindings == null) {
            throw nodeError(node, "formula parameter bindings are required");
        }
        String expanded = source.getEquation();
        for (String parameter : source.getParameters()) {
            FormulaExpressionNode binding = findBinding(bindings, parameter);
            if (binding == null) {
                throw nodeError(node, "missing binding for parameter " + parameter);
            }
            String replacement = compileNode(binding, context, depth + 1);
            expanded = replaceVariable(expanded, parameter, "(" + replacement + ")");
        }

        Set<String> expected = new LinkedHashSet<>(FormulaNormalizer.normalizeParameters(source.getParameters()));
        for (String supplied : bindings.keySet()) {
            if (supplied == null || !expected.contains(supplied.trim().toUpperCase())) {
                throw nodeError(node, "unexpected binding " + supplied);
            }
        }

        context.snapshots.putIfAbsent(source.getId(), new FormulaSourceSnapshot(
                source.getId(), source.getName(), source.getEquation(), List.copyOf(source.getParameters())));
        return "(" + expanded + ")";
    }

    private FormulaExpressionNode findBinding(Map<String, FormulaExpressionNode> bindings, String parameter) {
        for (Map.Entry<String, FormulaExpressionNode> entry : bindings.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(parameter)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String replaceVariable(String equation, String variable, String replacement) {
        Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(variable) + "(?![A-Za-z0-9_])");
        return pattern.matcher(equation).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private IllegalArgumentException nodeError(FormulaExpressionNode node, String message) {
        String nodeId = node.getId() == null || node.getId().isBlank() ? "unknown" : node.getId();
        return new IllegalArgumentException("node " + nodeId + ": " + message);
    }

    private static class CompilationContext {
        private int nodeCount;
        private final Set<String> parameters = new LinkedHashSet<>();
        private final Map<String, FormulaSourceSnapshot> snapshots = new LinkedHashMap<>();
    }
}

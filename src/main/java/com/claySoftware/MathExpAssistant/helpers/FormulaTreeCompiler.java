package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.models.ExpressionOperator;
import com.claySoftware.MathExpAssistant.models.ExpressionNodeType;
import com.claySoftware.MathExpAssistant.models.FormulaCompilationResult;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.MeasurementUnit;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaVersionStore;
import com.claySoftware.MathExpAssistant.utils.FormulaNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FormulaTreeCompiler {
    private static final int MAX_DEPTH = 30;
    private static final int MAX_NODES = 250;
    private static final int MAX_EQUATION_LENGTH = 10_000;
    private static final Pattern EXPRESSION_TOKENS = Pattern.compile(
            "\"[^\"]*\"|(?i)(?<![A-Z0-9_])[A-Z][A-Z0-9_]*(?![A-Z0-9_])");

    private final FormulaRepository formulaRepository;

    private final FormulaVersionStore versions;

    public FormulaTreeCompiler(FormulaRepository formulaRepository, FormulaVersionStore versions) {
        this.formulaRepository = formulaRepository;
        this.versions = versions;
    }

    public FormulaCompilationResult compile(FormulaExpressionNode root) {
        return compile(root, null);
    }

    public FormulaCompilationResult compile(FormulaExpressionNode root, FormulaEntity previous) {
        CompilationContext context = new CompilationContext(previous);
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
            return DecimalCalculation.decimal(new BigDecimal(node.getConstantValue().trim())).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException | DecimalCalculation.Failure ex) {
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
        FormulaSourceSnapshot source = resolveSource(node, context);
        if (source.getEquation() == null || source.getParameters() == null) {
            throw nodeError(node, "source formula is incomplete");
        }
        node.setSourceVersion(effectiveVersion(source));

        Map<String, FormulaExpressionNode> bindings = node.getBindings();
        if (bindings == null) {
            throw nodeError(node, "formula parameter bindings are required");
        }
        Map<String, String> replacements = new LinkedHashMap<>();
        for (String parameter : source.getParameters()) {
            FormulaExpressionNode binding = findBinding(bindings, parameter);
            if (binding == null) {
                throw nodeError(node, "missing binding for parameter " + parameter);
            }
            String replacement = compileNode(binding, context, depth + 1);
            if (source.getUnitSnapshots() != null) {
                replacement = checkUnit(replacement, source.getUnitSnapshots().get(parameter));
            }
            replacements.put(parameter.toUpperCase(Locale.ROOT), "(" + replacement + ")");
        }

        Set<String> expected = new LinkedHashSet<>(FormulaNormalizer.normalizeParameters(source.getParameters()));
        for (String supplied : bindings.keySet()) {
            if (supplied == null || !expected.contains(supplied.trim().toUpperCase())) {
                throw nodeError(node, "unexpected binding " + supplied);
            }
        }

        String expanded = bindVariables(source.getEquation(), replacements);
        context.snapshots.putIfAbsent(FormulaVersion.key(source.getFormulaId(), effectiveVersion(source)), source);
        if (source.getUnitSnapshots() != null) {
            expanded = checkUnit(expanded, source.getUnitSnapshots().get("_result"));
        }
        return "(" + expanded + ")";
    }

    private String checkUnit(String expression, MeasurementUnit unit) {
        return "UNITCHECK((" + expression + "), \"" + (unit == null ? "SCALAR" : unit.dimensionKey()) + "\")";
    }

    private FormulaSourceSnapshot resolveSource(FormulaExpressionNode node, CompilationContext context) {
        String id = node.getSourceFormulaId();
        Integer requested = node.getSourceVersion();
        if (requested != null && requested < 0) {
            throw nodeError(node, "sourceVersion must be non-negative");
        }
        List<FormulaSourceSnapshot> previousSources = context.previousSources.stream()
                .filter(snapshot -> id.equals(snapshot.getFormulaId())).toList();
        if (requested == null) {
            FormulaExpressionNode previous = context.previousNodes.get(node.getId());
            if (previous != null && id.equals(previous.getSourceFormulaId())) {
                requested = previous.getSourceVersion();
            }
            if (requested == null && previousSources.size() == 1) {
                return previousSources.getFirst();
            }
            if (requested == null && (previousSources.size() > 1 || context.previousReferences.contains(id))) {
                throw nodeError(node, "sourceVersion is required when a saved dependency cannot be identified");
            }
        }
        if (requested != null) {
            int number = requested;
            var previous = previousSources.stream().filter(snapshot -> effectiveVersion(snapshot) == number).findFirst();
            if (previous.isPresent()) {
                return previous.get();
            }
            if (number == 0) {
                throw nodeError(node, "legacy source snapshot was not found in this formula");
            }
            var historical = versions.find(id, number);
            if (historical.isPresent()) {
                return snapshot(node, historical.get());
            }
        }
        FormulaEntity current = formulaRepository.findByIdAndStatus(id, FormulaStatus.PUBLIC)
                .orElseThrow(() -> nodeError(node, "public source formula was not found"));
        if (requested != null && requested != FormulaVersion.currentNumber(current)) {
            throw nodeError(node, "public source version was not found");
        }
        return snapshot(node, FormulaVersion.from(current));
    }

    private FormulaSourceSnapshot snapshot(FormulaExpressionNode node, FormulaVersion source) {
        if (source.variable()) {
            throw nodeError(node, "variadic formulas are not supported in the builder yet");
        }
        return new FormulaSourceSnapshot(source.formulaId(), source.name(), source.equation(),
                source.parameters(), source.version(), source.unitSnapshots());
    }

    // Older snapshots may predate the live source used as catalog version 1.
    private static int effectiveVersion(FormulaSourceSnapshot source) {
        return Objects.requireNonNullElse(source.getVersion(), 0);
    }

    private FormulaExpressionNode findBinding(Map<String, FormulaExpressionNode> bindings, String parameter) {
        for (Map.Entry<String, FormulaExpressionNode> entry : bindings.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(parameter)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String bindVariables(String equation, Map<String, String> replacements) {
        Matcher tokens = EXPRESSION_TOKENS.matcher(equation);
        StringBuilder bound = new StringBuilder();
        while (tokens.find()) {
            String token = tokens.group();
            String replacement = replacements.get(token.toUpperCase(Locale.ROOT));
            if (replacement == null) continue;
            int next = tokens.end();
            while (next < equation.length() && Character.isWhitespace(equation.charAt(next))) next++;
            if (next < equation.length() && equation.charAt(next) == '(') continue;
            tokens.appendReplacement(bound, Matcher.quoteReplacement(replacement));
            if (bound.length() > MAX_EQUATION_LENGTH) {
                throw new IllegalArgumentException("compiled equation exceeds the maximum length");
            }
        }
        tokens.appendTail(bound);
        return bound.toString();
    }

    private IllegalArgumentException nodeError(FormulaExpressionNode node, String message) {
        String nodeId = node.getId() == null || node.getId().isBlank() ? "unknown" : node.getId();
        return new IllegalArgumentException("node " + nodeId + ": " + message);
    }

    private static class CompilationContext {
        private int nodeCount;
        private final Set<String> parameters = new LinkedHashSet<>();
        private final Map<String, FormulaSourceSnapshot> snapshots = new LinkedHashMap<>();
        private final Map<String, FormulaExpressionNode> previousNodes = new LinkedHashMap<>();
        private final List<FormulaSourceSnapshot> previousSources;
        private final Set<String> previousReferences = new LinkedHashSet<>();

        private CompilationContext(FormulaEntity previous) {
            previousSources = previous == null || previous.getSourceSnapshots() == null
                    ? List.of() : previous.getSourceSnapshots();
            if (previous != null) {
                indexNodes(previous.getExpressionTree());
            }
        }

        private void indexNodes(FormulaExpressionNode node) {
            if (node == null) {
                return;
            }
            if (node.getType() == ExpressionNodeType.FORMULA) {
                previousReferences.add(node.getSourceFormulaId());
                if (node.getId() != null) {
                    previousNodes.putIfAbsent(node.getId(), node);
                }
            }
            if (node.getChildren() != null) {
                node.getChildren().forEach(this::indexNodes);
            }
            if (node.getBindings() != null) {
                node.getBindings().values().forEach(this::indexNodes);
            }
        }
    }
}

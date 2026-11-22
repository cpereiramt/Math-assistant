package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation;
import com.claySoftware.MathExpAssistant.helpers.DecimalCalculation.Quantity;
import com.claySoftware.MathExpAssistant.helpers.EquationBuilder;
import com.claySoftware.MathExpAssistant.models.CalculationRequest;
import com.claySoftware.MathExpAssistant.models.CalculationRequest.Input;
import com.claySoftware.MathExpAssistant.models.CalculationResult;
import com.claySoftware.MathExpAssistant.models.MeasurementUnit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class CalculationService {
    private final UnitCatalog catalog;

    public CalculationService(UnitCatalog catalog) {
        this.catalog = catalog;
    }

    static boolean measured(FormulaEntity formula) {
        return formula.getOutputUnit() != null || formula.getInputUnits() != null && !formula.getInputUnits().isEmpty()
                || formula.getSourceSnapshots() != null && formula.getSourceSnapshots().stream()
                        .anyMatch(source -> source.getUnitSnapshots() != null);
    }

    void prepare(FormulaEntity formula, FormulaEntity previous) {
        formula.setUnitSnapshots(null);
        if (!measured(formula)) return;

        Map<String, MeasurementUnit> definitions = new HashMap<>();
        Map<String, String> inputUnits = formula.getInputUnits() == null ? Map.of() : formula.getInputUnits();
        Map<String, MeasurementUnit> oldDefinitions = previous == null || previous.getUnitSnapshots() == null
                ? Map.of() : previous.getUnitSnapshots();
        Map<String, String> oldInputs = previous == null || previous.getInputUnits() == null
                ? Map.of() : previous.getInputUnits();
        List<String> keys = Boolean.TRUE.equals(formula.isVariable()) ? List.of("ITEM") : formula.getParameters();
        if (keys == null || keys.size() > DecimalCalculation.MAX_VALUES || !keys.containsAll(inputUnits.keySet())) {
            throw new IllegalArgumentException("Unit settings must refer to declared variables");
        }
        Map<String, MeasurementUnit> resolved = new HashMap<>();
        Function<String, MeasurementUnit> active = code -> resolved.computeIfAbsent(code, catalog::requireActive);
        inputUnits.forEach((field, code) -> definitions.put(field,
                pin(code, oldInputs.get(field), oldDefinitions.get(field), active)));
        if (formula.getOutputUnit() != null) {
            definitions.put("_result", pin(formula.getOutputUnit(), previous == null ? null : previous.getOutputUnit(),
                    oldDefinitions.get("_result"), active));
        }

        boolean variadic = Boolean.TRUE.equals(formula.isVariable());
        if (variadic && ((formula.getMinParameters() != null && formula.getMinParameters() > DecimalCalculation.MAX_VALUES)
                || (formula.getMaxParameters() != null && formula.getMaxParameters() > DecimalCalculation.MAX_VALUES))) {
            throw new IllegalArgumentException("Measured formulas support at most " + DecimalCalculation.MAX_VALUES + " values");
        }
        String expression = variadic ? EquationBuilder.buildVariadicEquation(formula.getEquation(), 2) : formula.getEquation();
        Map<String, Quantity> types = new HashMap<>();
        for (String key : variadic ? List.of("X1", "X2") : keys) {
            types.put(key, new Quantity(BigDecimal.ONE, dimension(definitions.get(variadic ? "ITEM" : key))));
        }
        var result = DecimalCalculation.run(expression, types, true);
        DecimalCalculation.requireDimension(result, dimension(definitions.get("_result")), "result");
        formula.setUnitSnapshots(Map.copyOf(definitions));
    }

    private MeasurementUnit pin(String code, String previousCode, MeasurementUnit existing,
                                Function<String, MeasurementUnit> active) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("A unit code is required");
        return code.equals(previousCode) && existing != null ? existing : active.apply(code);
    }

    CalculationResult calculate(FormulaEntity formula, CalculationRequest request) {
        Map<String, MeasurementUnit> definitions = formula.getUnitSnapshots() == null ? Map.of() : formula.getUnitSnapshots();
        Map<String, Quantity> values = new HashMap<>();
        Map<String, MeasurementUnit> suppliedUnits = new HashMap<>();
        String expression = formula.getEquation();
        if (Boolean.TRUE.equals(formula.isVariable())) {
            List<Input> items = request.values();
            int min = formula.getMinParameters() == null ? 1 : formula.getMinParameters();
            int max = formula.getMaxParameters() == null ? DecimalCalculation.MAX_VALUES
                    : Math.min(DecimalCalculation.MAX_VALUES, formula.getMaxParameters());
            if (items == null || items.isEmpty() || items.size() < min || items.size() > max
                    || request.variables() != null) {
                throw new IllegalArgumentException("Provide between " + Math.max(1, min) + " and " + max + " values");
            }
            expression = EquationBuilder.buildVariadicEquation(expression, items.size());
            for (int i = 0; i < items.size(); i++) {
                String name = "X" + (i + 1);
                values.put(name, input(name, items.get(i), definitions.get("ITEM"), suppliedUnits));
            }
        } else {
            Map<String, Input> inputs = request.variables();
            List<String> parameters = formula.getParameters();
            if (inputs == null || parameters == null || request.values() != null
                    || inputs.size() > DecimalCalculation.MAX_VALUES
                    || !inputs.keySet().equals(java.util.Set.copyOf(parameters))) {
                throw new IllegalArgumentException("Expected variables: " + parameters);
            }
            inputs.forEach((name, value) -> values.put(name, input(name, value, definitions.get(name), suppliedUnits)));
        }

        Quantity evaluated = DecimalCalculation.run(expression, values, false);
        MeasurementUnit output = definitions.get("_result");
        DecimalCalculation.requireDimension(evaluated, dimension(output), "result");
        BigDecimal value = output == null ? evaluated.value()
                : DecimalCalculation.divide(evaluated.value(), output.factor());
        return new CalculationResult(DecimalCalculation.decimal(value).stripTrailingZeros().toPlainString(),
                output == null ? null : output.code());
    }

    private Quantity input(String name, Input input, MeasurementUnit expected,
                           Map<String, MeasurementUnit> suppliedUnits) {
        if (input == null || input.value() == null) throw new IllegalArgumentException("Missing value for " + name);
        BigDecimal value = DecimalCalculation.decimal(input.value());
        if (expected == null) {
            if (input.unit() != null) throw new IllegalArgumentException(name + " expects a unitless number");
            return new Quantity(value, Map.of());
        }
        MeasurementUnit supplied = expected;
        if (input.unit() != null && !input.unit().equals(expected.code())) {
            try {
                supplied = suppliedUnits.computeIfAbsent(input.unit(), catalog::requireActive);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(name + ": " + e.getMessage(), e);
            }
        }
        if (!expected.dimensionKey().equals(supplied.dimensionKey())) {
            throw new IllegalArgumentException(name + " expects a unit compatible with " + expected.code());
        }
        return new Quantity(DecimalCalculation.decimal(value.multiply(supplied.factor(), DecimalCalculation.PRECISION)),
                dimension(expected));
    }

    private static Map<String, Integer> dimension(MeasurementUnit unit) {
        return unit == null ? Map.of() : unit.dimensions();
    }
}

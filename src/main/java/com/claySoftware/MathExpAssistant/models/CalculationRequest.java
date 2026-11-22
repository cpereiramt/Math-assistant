package com.claySoftware.MathExpAssistant.models;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CalculationRequest(String formulaId, String formulaName, Map<String, Input> variables, List<Input> values) {
    public record Input(BigDecimal value, String unit) {}
}

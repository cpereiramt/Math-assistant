package com.claySoftware.MathExpAssistant.models;

import java.util.List;

public record FormulaCompilationResult(
        String equation,
        String displayEquation,
        List<String> parameters,
        List<FormulaSourceSnapshot> sourceSnapshots) {
}

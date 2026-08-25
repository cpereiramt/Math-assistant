package com.claySoftware.MathExpAssistant.models;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;

import java.util.List;

public record FormulaSearchResponse(
        List<FormulaEntity> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {
}

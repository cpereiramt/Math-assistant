package com.claySoftware.MathExpAssistant.models;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FormulaRatingRequest(
        @Min(value = -1, message = "value must be -1 or 1") @Max(value = 1, message = "value must be -1 or 1") Integer value) {
}
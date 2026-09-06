package com.claySoftware.MathExpAssistant.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FormulaCommentUpdateRequest(
        @NotBlank(message = "content is required") @Size(max = 2000, message = "content must contain at most 2000 characters") String content) {
}
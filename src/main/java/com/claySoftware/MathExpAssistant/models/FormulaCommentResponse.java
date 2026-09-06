package com.claySoftware.MathExpAssistant.models;

import java.time.Instant;
import java.util.List;

public record FormulaCommentResponse(
        String id,
        String formulaId,
        String userId,
        String content,
        String parentCommentId,
        Instant createdAt,
        Instant updatedAt,
        long replyCount,
        List<FormulaCommentResponse> replies) {
}
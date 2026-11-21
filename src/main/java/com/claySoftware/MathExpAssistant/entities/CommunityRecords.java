package com.claySoftware.MathExpAssistant.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

public final class CommunityRecords {
    private CommunityRecords() {}

    @Document("author_follows")
    @CompoundIndex(def = "{'authorId': 1, 'followerId': 1}")
    public record Follow(@Id String id, String authorId, String followerId) {}

    @Document("formula_views")
    public record View(@Id String id, @Indexed(expireAfter = "3d") Instant createdAt) {}

    @Document("community_quotas")
    public record Quota(@Id String id, int count, @Indexed(expireAfter = "3d") Instant createdAt) {}

    public enum ReportReason { SPAM, ABUSE, MATH_ERROR }
    public enum ReportTarget { FORMULA, COMMENT }
    public enum ReportDecision { DISMISSED, REMOVED }
    public enum NotificationType { APPROVED, REJECTED, COMMENT, RATING, CONTENT_REMOVED }

    @Document("moderated_formulas")
    public record ModeratedFormula(@Id String id, FormulaEntity formula, Instant removedAt) {}

    @Document("community_reports")
    @CompoundIndex(def = "{'decision': 1, 'createdAt': -1, '_id': -1}")
    public record Report(@Id String id, ReportTarget target, String targetId, String formulaId,
                         Integer formulaVersion, Instant commentUpdatedAt,
                         @JsonIgnore String reporter, ReportReason reason, String details,
                         String evidence, Instant createdAt, ReportDecision decision,
                         @JsonIgnore String reviewer, String resolution, Instant resolvedAt) {}

    @Document("community_notifications")
    @CompoundIndex(def = "{'recipient': 1, 'createdAt': -1, '_id': -1}")
    public record Notification(@Id String id, @JsonIgnore String recipient, NotificationType type,
                               String formulaId, String formulaName, String reason, Instant createdAt,
                               Instant readAt) {}
}

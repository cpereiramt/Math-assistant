package com.claySoftware.MathExpAssistant.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "formula_comments")
@CompoundIndex(name = "formula_comment_thread", def = "{'formulaId': 1, 'parentCommentId': 1, 'deleted': 1, 'createdAt': -1}")
@Getter
@Setter
public class FormulaCommentEntity {
    @Id
    private String id;

    private String formulaId;
    @JsonIgnore
    private String userId;
    private String content;
    private String parentCommentId;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
}
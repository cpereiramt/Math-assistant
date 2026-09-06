package com.claySoftware.MathExpAssistant.entities;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "formula_ratings")
@CompoundIndex(name = "unique_formula_rating", def = "{'formulaId': 1, 'userId': 1}", unique = true)
@Getter
@Setter
public class FormulaRatingEntity {
    @Id
    private String id;

    private String formulaId;
    private String userId;
    private int value;
    private Instant createdAt;
    private Instant updatedAt;
}
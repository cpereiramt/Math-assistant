package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document(collection = "formulas")
@CompoundIndexes({
        @CompoundIndex(name = "unique_formula_scope", def = "{'name': 1, 'group': 1, 'status': 1, 'ownerUserId': 1}", unique = true),
        @CompoundIndex(name = "unique_published_formula_name", def = "{'publicationKey': 1}", unique = true,
                partialFilter = "{'publicationKey': {'$type': 'string'}}"),
        @CompoundIndex(name = "formula_public_search", def = "{'status': 1, 'group': 1, 'variable': 1, 'name': 1}"),
        @CompoundIndex(name = "formula_owner_search", def = "{'ownerUserId': 1, 'status': 1, 'group': 1, 'variable': 1, 'name': 1}"),
        @CompoundIndex(name = "formula_social_search", def = "{'status': 1, 'upvotes': -1, 'averageRating': -1, 'createdAt': -1}")
})
@Getter
@Setter
public class FormulaEntity {
    @Id
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String id;

    private String name;

    private FormulaGroup group;

    private String description;

    private String equation;
    private String displayEquation;

    private FormulaInputMode inputMode;
    private FormulaExpressionNode expressionTree;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<FormulaSourceSnapshot> sourceSnapshots;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer builderVersion;

    private List<String> parameters;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private FormulaStatus status;
    private boolean variable;
    private Integer minParameters;
    private Integer maxParameters;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String ownerUserId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String ownerEmail;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant updatedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long upvotes;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long downvotes;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long ratingCount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private double averageRating;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long commentCount;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private long viewCount;

    @JsonIgnore
    private String editToken;
    @JsonIgnore
    private String publicationKey;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer version;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String reviewedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant reviewedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String reviewReason;

}

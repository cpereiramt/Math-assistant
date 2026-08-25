package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;

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
        @CompoundIndex(name = "formula_public_search", def = "{'status': 1, 'group': 1, 'variable': 1, 'name': 1}"),
        @CompoundIndex(name = "formula_owner_search", def = "{'ownerUserId': 1, 'status': 1, 'group': 1, 'variable': 1, 'name': 1}")
})
@Getter
@Setter
public class FormulaEntity {
    @Id
    private String id;

    private String name;

    private FormulaGroup group;

    private String description;

    private String equation;
    private String displayEquation;

    private FormulaInputMode inputMode;
    private FormulaExpressionNode expressionTree;
    private List<FormulaSourceSnapshot> sourceSnapshots;
    private Integer builderVersion;

    private List<String> parameters;

    private FormulaStatus status;
    private boolean variable;
    private Integer minParameters;
    private Integer maxParameters;

    private String ownerUserId;
    private String ownerEmail;
    private Instant createdAt;
    private Instant updatedAt;

}

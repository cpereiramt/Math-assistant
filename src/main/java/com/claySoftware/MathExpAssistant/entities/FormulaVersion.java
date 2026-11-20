package com.claySoftware.MathExpAssistant.entities;

import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.Instant;
import java.util.List;

@Document("formula_versions")
@CompoundIndex(name = "formula_version_history", def = "{'formulaId': 1, 'version': -1}", unique = true)
public record FormulaVersion(
        @Id @JsonIgnore String id,
        String formulaId,
        int version,
        String name,
        FormulaGroup group,
        String description,
        String equation,
        String displayEquation,
        List<String> parameters,
        boolean variable,
        Integer minParameters,
        Integer maxParameters,
        FormulaInputMode inputMode,
        FormulaExpressionNode expressionTree,
        List<FormulaSourceSnapshot> sourceSnapshots,
        Integer builderVersion,
        String reviewedBy,
        Instant reviewedAt,
        String reviewReason) {

    public static String key(String formulaId, int version) {
        return formulaId + ":" + version;
    }

    public static int currentNumber(FormulaEntity formula) {
        return formula.getVersion() == null ? 1 : formula.getVersion();
    }

    public static FormulaVersion from(FormulaEntity formula) {
        int number = currentNumber(formula);
        return new FormulaVersion(key(formula.getId(), number), formula.getId(), number,
                formula.getName(), formula.getGroup(), formula.getDescription(),
                formula.getEquation(), formula.getDisplayEquation(), formula.getParameters(),
                formula.isVariable(), formula.getMinParameters(), formula.getMaxParameters(),
                formula.getInputMode(), formula.getExpressionTree(), formula.getSourceSnapshots(),
                formula.getBuilderVersion(), formula.getReviewedBy(), formula.getReviewedAt(),
                formula.getReviewReason());
    }
}

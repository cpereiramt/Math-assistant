package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.helpers.FormulaTreeCompiler;
import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.models.ExpressionNodeType;
import com.claySoftware.MathExpAssistant.models.FormulaCompilationResult;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaInputMode;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormulaServiceHybridInputTest {
    private final FormulaRepository repository = mock(FormulaRepository.class);
    private final FormulaTreeCompiler treeCompiler = mock(FormulaTreeCompiler.class);
    private final FormulaService service = new FormulaService(
            new FormulaExecutor(), repository, new FormulaValidator(), treeCompiler);

    @Test
    void keepsLegacyTextPayloadCompatible() {
        FormulaEntity formula = baseFormula();
        formula.setEquation("x + 1");
        formula.setParameters(List.of("x"));

        String error = service.validateUserFormula(formula);

        assertThat(error).isNull();
        assertThat(formula.getInputMode()).isEqualTo(FormulaInputMode.TEXT);
        assertThat(formula.getEquation()).isEqualTo("X + 1");
        assertThat(formula.getParameters()).containsExactly("X");
    }

    @Test
    void builderPayloadUsesServerCompiledEquation() {
        FormulaEntity formula = baseFormula();
        formula.setInputMode(FormulaInputMode.BUILDER);
        formula.setEquation("UNTRUSTED_CLIENT_EQUATION");
        FormulaExpressionNode tree = new FormulaExpressionNode();
        tree.setId("variable-node");
        tree.setType(ExpressionNodeType.VARIABLE);
        tree.setVariableName("VALUE");
        formula.setExpressionTree(tree);
        when(treeCompiler.compile(tree)).thenReturn(new FormulaCompilationResult(
                "VALUE * 2", "VALUE * 2", List.of("VALUE"), List.of()));

        String error = service.validateUserFormula(formula);

        assertThat(error).isNull();
        assertThat(formula.getEquation()).isEqualTo("VALUE * 2");
        assertThat(formula.getParameters()).containsExactly("VALUE");
        assertThat(formula.getBuilderVersion()).isEqualTo(1);
    }

    private FormulaEntity baseFormula() {
        FormulaEntity formula = new FormulaEntity();
        formula.setName("CUSTOM");
        formula.setGroup(FormulaGroup.ALGEBRA);
        return formula;
    }
}

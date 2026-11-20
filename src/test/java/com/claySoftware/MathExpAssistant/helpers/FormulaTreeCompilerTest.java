package com.claySoftware.MathExpAssistant.helpers;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import com.claySoftware.MathExpAssistant.models.ExpressionNodeType;
import com.claySoftware.MathExpAssistant.models.ExpressionOperator;
import com.claySoftware.MathExpAssistant.models.FormulaExpressionNode;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaSourceSnapshot;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.FormulaVersionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormulaTreeCompilerTest {
    private final FormulaRepository repository = mock(FormulaRepository.class);
    private final FormulaVersionStore versions = mock(FormulaVersionStore.class);
    private final FormulaTreeCompiler compiler = new FormulaTreeCompiler(repository, versions);

    @Test
    void compilesOperatorsVariablesAndConstants() {
        FormulaExpressionNode root = operator("root", ExpressionOperator.MULTIPLY,
                variable("base", "BASE"), constant("two", "2"));

        var result = compiler.compile(root);

        assertThat(result.equation()).isEqualTo("((BASE) * (2))");
        assertThat(result.parameters()).containsExactly("BASE");
        assertThat(result.sourceSnapshots()).isEmpty();
    }

    @Test
    void expandsAnExistingPublicFormulaAndKeepsItsSnapshot() {
        FormulaEntity rectangleArea = new FormulaEntity();
        rectangleArea.setId("area-id");
        rectangleArea.setName("RECTANGLE_AREA");
        rectangleArea.setEquation("WIDTH * HEIGHT");
        rectangleArea.setParameters(List.of("WIDTH", "HEIGHT"));
        rectangleArea.setStatus(FormulaStatus.PUBLIC);
        when(repository.findByIdAndStatus("area-id", FormulaStatus.PUBLIC))
                .thenReturn(Optional.of(rectangleArea));

        FormulaExpressionNode formula = new FormulaExpressionNode();
        formula.setId("formula-node");
        formula.setType(ExpressionNodeType.FORMULA);
        formula.setSourceFormulaId("area-id");
        formula.setBindings(Map.of(
                "WIDTH", variable("width", "BASE"),
                "HEIGHT", variable("height", "ALTURA")));

        var result = compiler.compile(formula);

        assertThat(result.equation()).contains("(BASE) * (ALTURA)");
        assertThat(result.parameters()).containsExactlyInAnyOrder("BASE", "ALTURA");
        assertThat(result.sourceSnapshots()).singleElement()
                .satisfies(snapshot -> assertThat(snapshot.getFormulaId()).isEqualTo("area-id"));
    }

    @Test
    void reportsTheNodeThatHasAnUnconnectedFormulaParameter() {
        FormulaEntity source = new FormulaEntity();
        source.setId("source-id");
        source.setName("SUM_TWO");
        source.setEquation("A + B");
        source.setParameters(List.of("A", "B"));
        when(repository.findByIdAndStatus("source-id", FormulaStatus.PUBLIC))
                .thenReturn(Optional.of(source));

        FormulaExpressionNode formula = new FormulaExpressionNode();
        formula.setId("formula-node-7");
        formula.setType(ExpressionNodeType.FORMULA);
        formula.setSourceFormulaId("source-id");
        formula.setBindings(Map.of("A", constant("one", "1")));

        assertThatThrownBy(() -> compiler.compile(formula))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("node formula-node-7")
                .hasMessageContaining("missing binding for parameter B");
    }

    @Test
    void retainsTwoDifferentVersionsOfTheSameSourceAcrossEdits() {
        FormulaEntity source = new FormulaEntity();
        source.setId("source");
        source.setEquation("X+1");
        source.setParameters(List.of("X"));
        source.setVersion(1);
        when(versions.find("source", 1)).thenReturn(Optional.of(FormulaVersion.from(source)));
        source.setEquation("X+2");
        source.setVersion(2);
        when(versions.find("source", 2)).thenReturn(Optional.of(FormulaVersion.from(source)));

        FormulaExpressionNode savedTree = operator("root", ExpressionOperator.SUBTRACT,
                reference("older", 1), reference("newer", 2));
        var saved = compiler.compile(savedTree);
        FormulaEntity existing = new FormulaEntity();
        existing.setExpressionTree(savedTree);
        existing.setSourceSnapshots(saved.sourceSnapshots());

        FormulaExpressionNode editedTree = operator("root", ExpressionOperator.SUBTRACT,
                reference("older", null), reference("newer", null));
        var edited = compiler.compile(editedTree, existing);

        assertThat(editedTree.getChildren()).extracting(FormulaExpressionNode::getSourceVersion)
                .containsExactly(1, 2);
        assertThat(edited.equation()).isEqualTo(saved.equation());
        assertThat(edited.sourceSnapshots()).extracting(FormulaSourceSnapshot::getVersion)
                .containsExactly(1, 2);
    }

    private FormulaExpressionNode reference(String nodeId, Integer version) {
        FormulaExpressionNode reference = new FormulaExpressionNode();
        reference.setId(nodeId);
        reference.setType(ExpressionNodeType.FORMULA);
        reference.setSourceFormulaId("source");
        reference.setSourceVersion(version);
        reference.setBindings(Map.of("X", variable(nodeId + "-binding", "X")));
        return reference;
    }

    private FormulaExpressionNode variable(String id, String name) {
        FormulaExpressionNode node = new FormulaExpressionNode();
        node.setId(id);
        node.setType(ExpressionNodeType.VARIABLE);
        node.setVariableName(name);
        return node;
    }

    private FormulaExpressionNode constant(String id, String value) {
        FormulaExpressionNode node = new FormulaExpressionNode();
        node.setId(id);
        node.setType(ExpressionNodeType.CONSTANT);
        node.setConstantValue(value);
        return node;
    }

    private FormulaExpressionNode operator(
            String id,
            ExpressionOperator expressionOperator,
            FormulaExpressionNode... children) {
        FormulaExpressionNode node = new FormulaExpressionNode();
        node.setId(id);
        node.setType(ExpressionNodeType.OPERATOR);
        node.setOperator(expressionOperator);
        node.setChildren(List.of(children));
        return node;
    }
}

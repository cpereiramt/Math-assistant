package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.helpers.FormulaExecutor;
import com.claySoftware.MathExpAssistant.helpers.FormulaTreeCompiler;
import com.claySoftware.MathExpAssistant.helpers.FormulaValidator;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchResponse;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FormulaServiceSearchTest {
    private final FormulaRepository repository = mock(FormulaRepository.class);
    private final FormulaService service = new FormulaService(
            new FormulaExecutor(), repository, new FormulaValidator(), mock(FormulaTreeCompiler.class));

    @Test
    void searchesVariadicPublicFormulasWithPagination() {
        FormulaEntity formula = new FormulaEntity();
        formula.setName("ADDITION");
        formula.setVariable(true);
        when(repository.search(
                eq("add"), eq(List.of(FormulaGroup.ARITHMETIC)), eq(FormulaType.VARIADIC),
                eq(FormulaSearchScope.PUBLIC), eq("user-1"), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(
                        List.of(formula), invocation.getArgument(5), 1));

        FormulaSearchResponse response = service.searchFormulas(
                "add", List.of(FormulaGroup.ARITHMETIC), FormulaType.VARIADIC,
                FormulaSearchScope.PUBLIC, "user-1", 0, 20, "name", Sort.Direction.ASC);

        assertThat(response.content()).containsExactly(formula);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.sort()).isEqualTo("name,asc");
    }

    @Test
    void defaultsScopeToPublic() {
        when(repository.search(any(), any(), any(), eq(FormulaSearchScope.PUBLIC), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchFormulas(null, null, null, null, "user-1", 0, 20, "name", Sort.Direction.ASC);
    }

    @Test
    void rejectsUnsafePageSizeAndSortField() {
        assertThatThrownBy(() -> service.searchFormulas(
                null, null, null, FormulaSearchScope.PUBLIC, "user-1", 0, 101, "name", Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");

        assertThatThrownBy(() -> service.searchFormulas(
                null, null, null, FormulaSearchScope.PUBLIC, "user-1", 0, 20, "equation", Sort.Direction.ASC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sortBy");
    }
}

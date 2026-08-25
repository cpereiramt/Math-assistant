package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FormulaSearchRepository {
    Page<FormulaEntity> search(
            String query,
            List<FormulaGroup> groups,
            FormulaType type,
            FormulaSearchScope scope,
            String ownerUserId,
            Pageable pageable);
}

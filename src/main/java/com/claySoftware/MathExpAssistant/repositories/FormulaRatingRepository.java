package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaRatingEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FormulaRatingRepository extends MongoRepository<FormulaRatingEntity, String> {
    Optional<FormulaRatingEntity> findByFormulaIdAndUserId(String formulaId, String userId);

    long countByFormulaIdAndValue(String formulaId, int value);

    long countByFormulaId(String formulaId);
}
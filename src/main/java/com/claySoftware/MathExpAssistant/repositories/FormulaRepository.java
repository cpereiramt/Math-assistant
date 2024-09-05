package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface FormulaRepository extends MongoRepository<FormulaEntity, String> {
    Optional<FormulaEntity> findByName(String name);
}
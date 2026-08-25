package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FormulaRepository extends MongoRepository<FormulaEntity, String>, FormulaSearchRepository {
    Optional<FormulaEntity> findByName(String name);

    Optional<FormulaEntity> findByNameAndStatus(String name, FormulaStatus status);

    Optional<FormulaEntity> findByIdAndStatus(String id, FormulaStatus status);

    Optional<FormulaEntity> findByNameAndOwnerUserIdAndStatus(
            String name,
            String ownerUserId,
            FormulaStatus status);

    Optional<FormulaEntity> findByNameAndGroupAndOwnerUserIdAndStatus(
            String name,
            FormulaGroup group,
            String ownerUserId,
            FormulaStatus status);

    Optional<FormulaEntity> findByIdAndOwnerUserIdAndStatus(String id, String ownerUserId, FormulaStatus status);

    List<FormulaEntity> findAllByStatus(FormulaStatus status);

    List<FormulaEntity> findAllByOwnerUserIdAndStatus(String ownerUserId, FormulaStatus status);
}

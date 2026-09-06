package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaCommentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FormulaCommentRepository extends MongoRepository<FormulaCommentEntity, String> {
    Page<FormulaCommentEntity> findAllByFormulaIdAndDeletedFalse(String formulaId, Pageable pageable);

    Page<FormulaCommentEntity> findAllByFormulaIdAndParentCommentIdIsNullAndDeletedFalse(
            String formulaId, Pageable pageable);

    Page<FormulaCommentEntity> findAllByFormulaIdAndParentCommentIdAndDeletedFalse(
            String formulaId, String parentCommentId, Pageable pageable);

    long countByFormulaIdAndParentCommentIdAndDeletedFalse(String formulaId, String parentCommentId);

    long countByFormulaIdAndDeletedFalse(String formulaId);
}
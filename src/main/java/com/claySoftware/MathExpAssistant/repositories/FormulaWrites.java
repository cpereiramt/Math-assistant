package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class FormulaWrites {
    private final MongoTemplate mongo;

    public FormulaWrites(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void replace(FormulaEntity previous, FormulaEntity replacement) {
        replace(unchanged(previous, previous.getStatus()), replacement);
    }

    public void replaceFromStatus(FormulaEntity formula, FormulaStatus previousStatus) {
        replace(unchanged(formula, previousStatus), formula);
    }

    private void replace(Query condition, FormulaEntity replacement) {
        replacement.setEditToken(UUID.randomUUID().toString());
        // MongoDB retains the matched document ID on replacement.
        String id = replacement.getId();
        replacement.setId(null);
        try {
            if (mongo.findAndReplace(condition, replacement) == null) {
                throw new OptimisticLockingFailureException("formula changed concurrently");
            }
        } finally {
            replacement.setId(id);
        }
    }

    public void delete(FormulaEntity previous) {
        if (mongo.remove(unchanged(previous, previous.getStatus()), FormulaEntity.class).getDeletedCount() == 0) {
            throw new OptimisticLockingFailureException("formula changed concurrently");
        }
    }

    private Query unchanged(FormulaEntity formula, FormulaStatus status) {
        return Query.query(Criteria.where("id").is(formula.getId())
                .and("status").is(status)
                .and("editToken").is(formula.getEditToken()));
    }
}

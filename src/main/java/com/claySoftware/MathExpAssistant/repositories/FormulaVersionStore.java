package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.ModeratedFormula;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.FormulaVersion;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FormulaVersionStore {
    private final MongoTemplate mongo;

    public FormulaVersionStore(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void append(FormulaEntity formula) {
        mongo.insert(FormulaVersion.from(formula));
    }

    public Optional<FormulaVersion> find(String formulaId, int version) {
        if (isModerated(formulaId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mongo.findById(FormulaVersion.key(formulaId, version), FormulaVersion.class))
                .filter(stored -> formulaId.equals(stored.formulaId()) && version == stored.version());
    }

    private boolean isModerated(String formulaId) {
        return mongo.exists(Query.query(Criteria.where("id").is(formulaId)), ModeratedFormula.class);
    }

    public List<FormulaVersion> history(String formulaId) {
        if (isModerated(formulaId)) {
            return List.of();
        }
        return mongo.find(Query.query(Criteria.where("formulaId").is(formulaId))
                .with(Sort.by(Sort.Direction.DESC, "version")), FormulaVersion.class);
    }
}

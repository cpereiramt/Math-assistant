package com.claySoftware.MathExpAssistant.repositories;

import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.models.FormulaGroup;
import com.claySoftware.MathExpAssistant.models.FormulaSearchScope;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.FormulaType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
public class FormulaSearchRepositoryImpl implements FormulaSearchRepository {
    private final MongoTemplate mongoTemplate;

    public FormulaSearchRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<FormulaEntity> search(
            String query,
            List<FormulaGroup> groups,
            FormulaType type,
            FormulaSearchScope scope,
            String ownerUserId,
            Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (scope == FormulaSearchScope.MINE) {
            filters.add(Criteria.where("status").is(FormulaStatus.PRIVATE));
            filters.add(Criteria.where("ownerUserId").is(ownerUserId));
        } else {
            filters.add(Criteria.where("status").is(FormulaStatus.PUBLIC));
        }

        if (query != null && !query.isBlank()) {
            Pattern pattern = Pattern.compile(Pattern.quote(query.trim()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            filters.add(new Criteria().orOperator(
                    Criteria.where("name").regex(pattern),
                    Criteria.where("description").regex(pattern),
                    Criteria.where("displayEquation").regex(pattern)));
        }
        if (groups != null && !groups.isEmpty()) {
            filters.add(Criteria.where("group").in(groups));
        }
        if (type != null) {
            filters.add(Criteria.where("variable").is(type == FormulaType.VARIADIC));
        }

        Criteria criteria = new Criteria().andOperator(filters.toArray(Criteria[]::new));
        Query filteredQuery = new Query(criteria);
        long total = mongoTemplate.count(filteredQuery, FormulaEntity.class);
        filteredQuery.with(pageable);
        List<FormulaEntity> content = mongoTemplate.find(filteredQuery, FormulaEntity.class);
        return new PageImpl<>(content, pageable, total);
    }
}

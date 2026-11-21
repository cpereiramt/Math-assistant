package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.View;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.repositories.CommunityTransactions;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static com.claySoftware.MathExpAssistant.models.FormulaStatus.PUBLIC;

@Service
public class FormulaDiscoveryService {
    private final FormulaRepository formulas;
    private final MongoTemplate mongo;
    private final CommunityTransactions transactions;
    private final PublicAuthorService authors;

    public FormulaDiscoveryService(FormulaRepository formulas, MongoTemplate mongo,
                                   CommunityTransactions transactions, PublicAuthorService authors) {
        this.formulas = formulas;
        this.mongo = mongo;
        this.transactions = transactions;
        this.authors = authors;
    }

    public FormulaEntity detail(String id) {
        return formulas.findByIdAndStatus(id, PUBLIC)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "public formula not found"));
    }

    public void recordView(String id, String viewer) {
        Instant now = Instant.now();
        String accountId = authors.account(viewer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "account required")).getId();
        String key = CommunityEvents.key(id, accountId, now.truncatedTo(ChronoUnit.DAYS).toString());
        transactions.write(() -> {
            detail(id);
            Query event = Query.query(Criteria.where("id").is(key));
            if (mongo.upsert(event, new Update().setOnInsert("createdAt", now), View.class).getUpsertedId() != null) {
                // The event and counter commit together. Changing the counter locks the active formula version.
                mongo.updateFirst(Query.query(Criteria.where("id").is(id).and("status").is(PUBLIC)),
                        new Update().inc("viewCount", 1), FormulaEntity.class);
            }
            return null;
        });
    }

    public Page<FormulaEntity> ranking(int days, Pageable page) {
        if (days != 7 && days != 30 && days != 90) {
            throw new IllegalArgumentException("ranking period must be 7, 30 or 90 days");
        }
        Instant now = Instant.now();
        // A vote keeps its initial timestamp when edited. New accounts cannot contribute their early votes.
        Aggregation pipeline = Aggregation.newAggregation(
                ignored -> new Document("$match", new Document("createdAt", new Document("$gte",
                        Date.from(now.minus(days, ChronoUnit.DAYS))).append("$lte", Date.from(now)))
                        .append("value", new Document("$in", List.of(-1, 1)))),
                ignored -> new Document("$lookup", new Document("from", "users")
                        .append("localField", "userId").append("foreignField", "email").append("as", "account")),
                ignored -> new Document("$unwind", "$account"),
                ignored -> Document.parse("""
                        {"$match":{"account.createdAt":{"$type":"date"},"$expr":{"$lte":[
                          "$account.createdAt",{"$subtract":["$createdAt",86400000]}
                        ]}}}
                        """),
                ignored -> Document.parse("""
                        {"$lookup":{"from":"formulas","let":{"formulaId":"$formulaId","objectId":{"$convert":{
                          "input":"$formulaId","to":"objectId","onError":null,"onNull":null
                        }}},"pipeline":[{"$match":{"status":"PUBLIC","$expr":{"$or":[{"$eq":["$_id","$$formulaId"]},{"$eq":["$_id","$$objectId"]}]}}}],
                        "as":"formula"}}
                        """),
                ignored -> new Document("$unwind", "$formula"),
                ignored -> Document.parse("""
                        {"$match":{"$expr":{"$and":[
                          {"$ne":["$userId",{"$ifNull":["$formula.ownerUserId",""]}]},
                          {"$ne":["$userId",{"$ifNull":["$formula.ownerEmail",""]}]},
                          {"$ne":[{"$toString":"$account._id"},{"$ifNull":["$formula.ownerUserId",""]}]},
                          {"$ne":[{"$toString":"$account._id"},{"$ifNull":["$formula.authorId",""]}]}
                        ]}}}
                        """),
                ignored -> Document.parse("""
                        {"$group":{"_id":"$formula._id","net":{"$sum":"$value"},"votes":{"$sum":1},
                          "formula":{"$first":"$formula"}}}
                        """),
                ignored -> Document.parse("""
                        {"$set":{"score":{"$divide":["$net",{"$add":["$votes",5]}]}}}
                        """),
                ignored -> new Document("$facet", new Document("entries", List.of(
                        Document.parse("{\"$sort\":{\"score\":-1,\"votes\":-1,\"_id\":1}}"),
                        new Document("$skip", page.getOffset()), new Document("$limit", page.getPageSize()),
                        new Document("$replaceWith", "$formula")))
                        .append("total", List.of(new Document("$count", "count")))));
        Document result = mongo.aggregate(pipeline, "formula_ratings", Document.class).getUniqueMappedResult();
        List<FormulaEntity> entries = result.getList("entries", Document.class).stream()
                .map(doc -> mongo.getConverter().read(FormulaEntity.class, doc)).toList();
        List<Document> total = result.getList("total", Document.class);
        return new PageImpl<>(entries, page, total.isEmpty() ? 0 : total.getFirst().get("count", Number.class).longValue());
    }
}

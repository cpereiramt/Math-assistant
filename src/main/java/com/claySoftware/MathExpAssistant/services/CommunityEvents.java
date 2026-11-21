package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Notification;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.NotificationType;
import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Quota;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
public class CommunityEvents {
    private final MongoTemplate mongo;

    public CommunityEvents(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void notify(String event, String recipient, String actor, NotificationType type,
                       FormulaEntity formula, String reason) {
        if (recipient == null || recipient.isBlank() || recipient.equals(actor)) {
            return;
        }
        Query query = Query.query(Criteria.where("id").is(key(event, recipient, type.name())));
        mongo.upsert(query, new Update().setOnInsert("recipient", recipient)
                .setOnInsert("type", type).setOnInsert("formulaId", formula.getId())
                .setOnInsert("formulaName", formula.getName()).setOnInsert("reason", reason)
                .setOnInsert("createdAt", Instant.now()), Notification.class);
    }

    public Page<Notification> inbox(String recipient, Pageable page) {
        Query query = Query.query(Criteria.where("recipient").is(recipient));
        long total = mongo.count(query, Notification.class);
        query.with(page).with(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return new PageImpl<>(mongo.find(query, Notification.class), page, total);
    }

    public void markRead(String recipient, String id) {
        Query query = Query.query(Criteria.where("recipient").is(recipient).and("id").is(id).and("readAt").is(null));
        mongo.updateFirst(query, new Update().set("readAt", Instant.now()), Notification.class);
    }

    public void limit(String action, String actor, int limit) {
        Instant now = Instant.now();
        String bucket = dailyBucket(action, actor, now);
        Quota quota = mongo.findAndModify(Query.query(Criteria.where("id").is(bucket)),
                new Update().inc("count", 1).setOnInsert("createdAt", now),
                FindAndModifyOptions.options().upsert(true).returnNew(true), Quota.class);
        if (quota.count() > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "daily " + action + " limit reached");
        }
    }

    public void consume(String action, String actor, int limit) {
        Instant now = Instant.now();
        Query available = Query.query(Criteria.where("id").is(dailyBucket(action, actor, now))
                .and("count").lt(limit));
        Update increment = new Update().inc("count", 1).setOnInsert("createdAt", now);
        try {
            mongo.upsert(available, increment, Quota.class);
        } catch (DuplicateKeyException existingBucket) {
            if (mongo.updateFirst(available, increment, Quota.class).getMatchedCount() == 0) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "daily " + action + " limit reached");
            }
        }
    }

    public int usage(String action, String actor) {
        Quota quota = mongo.findById(dailyBucket(action, actor, Instant.now()), Quota.class);
        return quota == null ? 0 : quota.count();
    }

    private String dailyBucket(String action, String actor, Instant now) {
        return key(action, actor, now.truncatedTo(ChronoUnit.DAYS).toString());
    }

    static String key(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

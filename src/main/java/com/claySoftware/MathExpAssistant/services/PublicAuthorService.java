package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.CommunityRecords.Follow;
import com.claySoftware.MathExpAssistant.entities.FormulaEntity;
import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static com.claySoftware.MathExpAssistant.models.FormulaStatus.PUBLIC;

@Service
public class PublicAuthorService {
    private final UserRepository users;
    private final FormulaRepository formulas;
    private final MongoTemplate mongo;

    public PublicAuthorService(UserRepository users, FormulaRepository formulas, MongoTemplate mongo) {
        this.users = users;
        this.formulas = formulas;
        this.mongo = mongo;
    }

    public Optional<UserEntity> account(String identity) {
        if (identity == null || identity.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(identity).or(() -> users.findById(identity));
    }

    public String recipient(String identity) {
        return account(identity).map(UserEntity::getEmail).orElse(identity);
    }

    public Optional<UserEntity> formulaAccount(FormulaEntity formula) {
        if (formula.getAuthorId() != null) {
            return users.findById(formula.getAuthorId());
        }
        return account(formula.getOwnerUserId()).or(() -> account(formula.getOwnerEmail()));
    }

    public String formulaRecipient(FormulaEntity formula) {
        return formulaAccount(formula).map(UserEntity::getEmail)
                .orElse(formula.getOwnerUserId() == null ? formula.getOwnerEmail() : formula.getOwnerUserId());
    }

    public Profile byFormula(String formulaId, String viewer) {
        FormulaEntity formula = formulas.findByIdAndStatus(formulaId, PUBLIC)
                .orElseThrow(() -> missing("public formula"));
        return profile(formulaAccount(formula).orElseThrow(() -> missing("author")), viewer);
    }

    public Profile byId(String id, String viewer) {
        return profile(author(id), viewer);
    }

    public Profile mine(String viewer) {
        return profile(account(viewer).orElseThrow(() -> missing("author")), viewer);
    }

    public Profile edit(String viewer, String name, String bio, String website) {
        UserEntity user = account(viewer).orElseThrow(() -> missing("author"));
        mongo.updateFirst(Query.query(Criteria.where("id").is(user.getId())),
                new Update().set("publicName", name.strip()).set("publicBio", bio.strip())
                        .set("publicWebsite", website.strip()), UserEntity.class);
        return byId(user.getId(), viewer);
    }

    public Page<FormulaEntity> publications(String id, Pageable page) {
        UserEntity user = author(id);
        return formulas.findPublications(user.getId(), user.getEmail(), page);
    }

    public void follow(String id, String viewer, boolean enabled) {
        String followerId = account(viewer).orElseThrow(() -> missing("author")).getId();
        author(id);
        if (id.equals(followerId)) {
            throw new IllegalArgumentException("authors cannot follow themselves");
        }
        Query key = Query.query(Criteria.where("id").is(CommunityEvents.key(id, followerId)));
        if (enabled) {
            mongo.upsert(key, new Update().setOnInsert("authorId", id).setOnInsert("followerId", followerId),
                    Follow.class);
        } else {
            mongo.remove(key, Follow.class);
        }
    }

    private UserEntity author(String id) {
        return users.findById(id).orElseThrow(() -> missing("author"));
    }

    private Profile profile(UserEntity user, String viewer) {
        long followers = mongo.count(Query.query(Criteria.where("authorId").is(user.getId())), Follow.class);
        boolean own = user.getEmail().equals(viewer);
        boolean following = !own && account(viewer).map(follower -> mongo.exists(
                Query.query(Criteria.where("id").is(CommunityEvents.key(user.getId(), follower.getId()))),
                Follow.class)).orElse(false);
        return new Profile(user.getId(), name(user), user.getPictureUrl(), user.getPublicBio(),
                user.getPublicWebsite(), followers, following, own);
    }

    public static String name(UserEntity user) {
        return user.getPublicName() == null ? user.getName() : user.getPublicName();
    }

    private static ResponseStatusException missing(String resource) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " not found");
    }

    public record Profile(String id, String name, String avatarUrl, String bio, String website,
                          long followers, boolean following, boolean own) {}
}

package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.models.FormulaStatus;
import com.claySoftware.MathExpAssistant.models.PlanUsage;
import com.claySoftware.MathExpAssistant.models.PlanUsage.Consumption;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.repositories.CommunityTransactions;
import com.claySoftware.MathExpAssistant.repositories.FormulaRepository;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.Supplier;

@Service
public class PlanLimits {

    private final boolean enabled;
    private final UserRepository users;
    private final UserService userService;
    private final FormulaRepository formulas;
    private final MongoTemplate mongo;
    private final CommunityTransactions transactions;
    private final CommunityEvents events;

    public PlanLimits(Environment environment, UserRepository users, UserService userService,
                      FormulaRepository formulas, MongoTemplate mongo, CommunityTransactions transactions,
                      CommunityEvents events) {
        this.enabled = environment.matchesProfiles("staging");
        this.users = users;
        this.userService = userService;
        this.formulas = formulas;
        this.mongo = mongo;
        this.transactions = transactions;
        this.events = events;
    }

    public String createPrivateFormula(String email, Supplier<String> insert) {
        if (!enabled) {
            return insert.get();
        }

        return transactions.write(() -> {
            // Write to the same account in this transaction to serialize concurrent formula creation.
            UserEntity user = mongo.findAndModify(Query.query(Criteria.where("email").is(email)),
                    new Update().inc("privateFormulaQuotaRevision", 1L),
                    FindAndModifyOptions.options().returnNew(true), UserEntity.class);
            Limits limits = limits(requireUser(user));
            if (formulas.countByOwnerUserIdAndStatus(email, FormulaStatus.PRIVATE) >= limits.privateFormulas()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "private formula limit reached");
            }
            return insert.get();
        });
    }

    public void consumeExecution(String email) {
        if (enabled) {
            UserEntity user = account(email);
            events.consume("formula-execution", user.getId(), limits(user).dailyExecutions());
        }
    }

    public void consumeBuilderPreview(String email) {
        if (enabled) {
            UserEntity user = account(email);
            events.consume("builder-preview", user.getId(), limits(user).dailyBuilderPreviews());
        }
    }

    public PlanUsage usage(String email) {
        if (!enabled) {
            return null;
        }

        UserEntity user = account(email);
        Limits limits = limits(user);
        return new PlanUsage(
                new Consumption(formulas.countByOwnerUserIdAndStatus(email, FormulaStatus.PRIVATE),
                        limits.privateFormulas()),
                new Consumption(events.usage("formula-execution", user.getId()), limits.dailyExecutions()),
                new Consumption(events.usage("builder-preview", user.getId()), limits.dailyBuilderPreviews()));
    }

    private UserEntity account(String email) {
        return requireUser(users.findByEmail(email).orElse(null));
    }

    private UserEntity requireUser(UserEntity user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "account is unavailable");
        }
        return user;
    }

    private Limits limits(UserEntity user) {
        return userService.effectivePlan(user) == UserPlan.PREMIUM
                ? new Limits(100, 500, 200)
                : new Limits(10, 50, 20);
    }

    private record Limits(int privateFormulas, int dailyExecutions, int dailyBuilderPreviews) {}
}

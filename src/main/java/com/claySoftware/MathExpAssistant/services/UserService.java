package com.claySoftware.MathExpAssistant.services;

import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.claySoftware.MathExpAssistant.utils.AdminBypass;
import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.exceptions.UserNotFoundException;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.models.UserProfileResponse;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MongoTemplate mongo;
    private final AdminBypass adminBypass;

    public UserService(UserRepository userRepository, MongoTemplate mongo, AdminBypass adminBypass) {
        this.userRepository = userRepository;
        this.mongo = mongo;
        this.adminBypass = adminBypass;
    }

    public UserEntity upsertOAuthUser(String sub, String email, String name, String pictureUrl, String role) {
        Instant now = Instant.now();
        Query identity = Query.query(Criteria.where("providerUserId").is(sub));
        Update profile = new Update()
                .set("provider", "google")
                .set("email", email)
                .set("name", name)
                .set("pictureUrl", pictureUrl)
                .set("lastLoginAt", now)
                .set("role", role)
                .set("updatedAt", now)
                .setOnInsert("createdAt", now)
                .setOnInsert("plan", UserPlan.FREE);

        try {
            return mongo.findAndModify(identity, profile,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), UserEntity.class);
        } catch (DuplicateKeyException conflict) {
            UserEntity existing = mongo.findAndModify(identity, profile,
                    FindAndModifyOptions.options().returnNew(true), UserEntity.class);
            if (existing == null) {
                throw conflict;
            }
            return existing;
        }
    }

    public UserPlan effectivePlan(UserEntity user) {
        if (adminBypass.isAdminEmail(user.getEmail())) {
            return UserPlan.PREMIUM;
        }
        return user.getPlan() == null ? UserPlan.FREE : user.getPlan();
    }

    public UserProfileResponse getCurrentUserProfile(String email) {
        if (email == null || email.isBlank()) {
            throw new UserNotFoundException();
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new);

        return new UserProfileResponse(
                user.getName(),
                user.getEmail(),
                user.getPictureUrl(),
                effectivePlan(user),
                user.getRole());
    }
}

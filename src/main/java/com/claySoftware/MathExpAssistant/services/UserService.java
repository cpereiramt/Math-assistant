package com.claySoftware.MathExpAssistant.services;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;

    }

    public UserEntity upsertOAuthUser(String sub, String email, String name, String pictureUrl, String plan,
            String role) {
        return userRepository.findByProviderUserId(sub)
                .map(u -> {
                    u.setPlan(UserPlan.valueOf(plan));
                    u.setLastLoginAt(Instant.now());
                    u.setRole(role);
                    u.setUpdatedAt(Instant.now());
                    return userRepository.save(u);
                })
                .orElseGet(() -> {
                    UserEntity u = new UserEntity();
                    u.setProvider("google");
                    u.setProviderUserId(sub);
                    u.setEmail(email);
                    u.setName(name);
                    u.setRole(role);
                    u.setPlan(UserPlan.valueOf(plan));
                    u.setPictureUrl(pictureUrl);
                    u.setCreatedAt(Instant.now());
                    u.setLastLoginAt(Instant.now());
                    u.setUpdatedAt(Instant.now());

                    return userRepository.save(u);
                });
    }
}

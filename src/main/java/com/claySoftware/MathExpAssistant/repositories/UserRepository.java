package com.claySoftware.MathExpAssistant.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.claySoftware.MathExpAssistant.entities.UserEntity;

import java.util.Optional;

public interface UserRepository extends MongoRepository<UserEntity, String> {
    Optional<UserEntity> findByProviderUserId(String providerUserId);

    Optional<UserEntity> findByEmail(String email);
}

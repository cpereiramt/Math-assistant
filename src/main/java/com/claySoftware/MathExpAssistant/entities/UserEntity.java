package com.claySoftware.MathExpAssistant.entities;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.claySoftware.MathExpAssistant.models.UserPlan;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Document(collection = "users")
@Getter
@Setter
public class UserEntity {

    @Id
    private String id;

    @Indexed
    private String provider; // "google"

    // Chave estável do usuário no provedor (OIDC "sub")
    @Indexed(unique = true)
    private String providerUserId;

    @Indexed(unique = true)
    private String email;

    private String name;
    private String pictureUrl;
    private UserPlan plan;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastLoginAt;
    private String role;

    // getters/setters
}

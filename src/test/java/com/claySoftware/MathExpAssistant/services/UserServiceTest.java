package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.exceptions.UserNotFoundException;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.models.UserProfileResponse;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;
import com.claySoftware.MathExpAssistant.utils.AdminBypass;
import org.bson.Document;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoTemplate mongo;

    @Mock
    private AdminBypass adminBypass;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, mongo, adminBypass);
    }

    @Test
    void shouldReturnCurrentUserProfile() {
        UserEntity user = user("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserProfileResponse profile = userService.getCurrentUserProfile("user@example.com");

        assertEquals("Example User", profile.name());
        assertEquals("user@example.com", profile.email());
        assertEquals("https://example.com/picture.jpg", profile.pictureUrl());
        assertEquals(UserPlan.FREE, profile.plan());
        assertEquals("USER", profile.role());
    }

    @Test
    void shouldRejectMissingProfile() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getCurrentUserProfile("missing@example.com"));
    }

    @Test
    void oauthLoginRefreshesProfileAndInitializesPlanOnlyOnInsert() {
        UserEntity premium = user("user@example.com");
        premium.setPlan(UserPlan.PREMIUM);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(UserEntity.class))).thenReturn(premium);

        UserEntity saved = userService.upsertOAuthUser(
                "google-sub", "user@example.com", "Updated Name", null, "USER");

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(), any(FindAndModifyOptions.class),
                eq(UserEntity.class));
        Document changes = update.getValue().getUpdateObject();
        assertEquals("Updated Name", changes.get("$set", Document.class).get("name"));
        assertFalse(changes.get("$set", Document.class).containsKey("plan"));
        assertEquals(UserPlan.FREE, changes.get("$setOnInsert", Document.class).get("plan"));
        assertEquals(UserPlan.PREMIUM, userService.effectivePlan(saved));
    }

    private UserEntity user(String email) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setName("Example User");
        user.setPictureUrl("https://example.com/picture.jpg");
        user.setPlan(UserPlan.FREE);
        user.setRole("USER");
        return user;
    }
}

package com.claySoftware.MathExpAssistant.services;

import com.claySoftware.MathExpAssistant.entities.UserEntity;
import com.claySoftware.MathExpAssistant.exceptions.UserNotFoundException;
import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.models.UserProfileResponse;
import com.claySoftware.MathExpAssistant.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
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
    void shouldSynchronizeOAuthProfileOnLogin() {
        UserEntity user = user("old@example.com");
        when(userRepository.findByProviderUserId("google-sub")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserEntity saved = userService.upsertOAuthUser(
                "google-sub",
                "new@example.com",
                "Updated Name",
                "https://example.com/new-picture.jpg",
                "PREMIUM",
                "ADMIN");

        assertEquals("google", saved.getProvider());
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("Updated Name", saved.getName());
        assertEquals("https://example.com/new-picture.jpg", saved.getPictureUrl());
        assertEquals(UserPlan.PREMIUM, saved.getPlan());
        assertEquals("ADMIN", saved.getRole());
        verify(userRepository).save(user);
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

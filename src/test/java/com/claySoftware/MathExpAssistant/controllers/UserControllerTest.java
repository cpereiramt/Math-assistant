package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.models.UserPlan;
import com.claySoftware.MathExpAssistant.models.UserProfileResponse;
import com.claySoftware.MathExpAssistant.services.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void shouldUseAuthenticatedIdentityToLoadProfile() {
        UserService userService = mock(UserService.class);
        Authentication authentication = mock(Authentication.class);
        UserProfileResponse expected = new UserProfileResponse(
                "Example User",
                "user@example.com",
                "https://example.com/picture.jpg",
                UserPlan.FREE,
                "USER");
        when(authentication.getName()).thenReturn("user@example.com");
        when(userService.getCurrentUserProfile("user@example.com")).thenReturn(expected);

        UserProfileResponse response = new UserController(userService).getCurrentUser(authentication);

        assertSame(expected, response);
        verify(userService).getCurrentUserProfile("user@example.com");
    }
}

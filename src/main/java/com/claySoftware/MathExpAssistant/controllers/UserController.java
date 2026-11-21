package com.claySoftware.MathExpAssistant.controllers;

import com.claySoftware.MathExpAssistant.models.PlanUsage;
import com.claySoftware.MathExpAssistant.models.UserProfileResponse;
import com.claySoftware.MathExpAssistant.services.UserService;
import com.claySoftware.MathExpAssistant.services.PlanLimits;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    private final PlanLimits planLimits;

    public UserController(UserService userService, PlanLimits planLimits) {
        this.userService = userService;
        this.planLimits = planLimits;
    }

    @Operation(summary = "Return the authenticated user's profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned successfully"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or expired access token"),
            @ApiResponse(responseCode = "404", description = "Profile not found")
    })
    @GetMapping("/me")
    public UserProfileResponse getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        UserProfileResponse profile = userService.getCurrentUserProfile(email);
        PlanUsage usage = planLimits.usage(email);
        return usage == null ? profile : new UserProfileResponse(profile.name(), profile.email(), profile.pictureUrl(),
                profile.plan(), profile.role(), usage);
    }
}

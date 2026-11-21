package com.claySoftware.MathExpAssistant.models;

public record UserProfileResponse(
        String name,
        String email,
        String pictureUrl,
        UserPlan plan,
        String role) {
}

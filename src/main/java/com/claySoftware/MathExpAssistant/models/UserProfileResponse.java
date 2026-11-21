package com.claySoftware.MathExpAssistant.models;

import com.fasterxml.jackson.annotation.JsonInclude;

public record UserProfileResponse(
        String name,
        String email,
        String pictureUrl,
        UserPlan plan,
        String role,
        @JsonInclude(JsonInclude.Include.NON_NULL) PlanUsage usage) {

    public UserProfileResponse(String name, String email, String pictureUrl, UserPlan plan, String role) {
        this(name, email, pictureUrl, plan, role, null);
    }
}

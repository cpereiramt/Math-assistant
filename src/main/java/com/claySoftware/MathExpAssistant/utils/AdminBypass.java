package com.claySoftware.MathExpAssistant.utils;

import org.springframework.stereotype.Component;

import com.claySoftware.MathExpAssistant.models.UserPlan;

@Component
public class AdminBypass {

    private static final String ADMIN_EMAIL = "cpereiramt@gmail.com";

    public boolean isAdminEmail(String email) {
        return ADMIN_EMAIL.equalsIgnoreCase(email);
    }

    public UserPlan effectivePlan(String email) {
        return isAdminEmail(email) ? UserPlan.PREMIUM : UserPlan.FREE;
    }
}

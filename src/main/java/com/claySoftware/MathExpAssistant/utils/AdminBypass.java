package com.claySoftware.MathExpAssistant.utils;

import com.claySoftware.MathExpAssistant.model.UserPlan;

import org.springframework.stereotype.Component;

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

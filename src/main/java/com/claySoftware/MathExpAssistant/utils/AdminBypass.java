package com.claySoftware.MathExpAssistant.utils;

import org.springframework.stereotype.Component;

import org.springframework.core.env.Environment;

@Component
public class AdminBypass {

    private final Environment environment;

    public AdminBypass(Environment environment) {
        this.environment = environment;
    }

    public boolean isAdminEmail(String email) {
        String ADMIN_EMAIL = this.environment.getProperty("ADMIN_CREDS");
        return ADMIN_EMAIL != null && email != null && ADMIN_EMAIL.equalsIgnoreCase(email);
    }
}

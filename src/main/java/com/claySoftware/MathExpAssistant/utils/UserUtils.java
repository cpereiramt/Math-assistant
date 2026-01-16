package com.claySoftware.MathExpAssistant.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

public class UserUtils {

    // Utility method to get current logged-in UserDetails
    public static UserDetails getCurrentUserDetails() {
        // Step 1: Get SecurityContext from SecurityContextHolder
        SecurityContext securityContext = SecurityContextHolder.getContext();

        // Step 2: Get Authentication object
        Authentication authentication = securityContext.getAuthentication();

        // Step 3 & 4: Validate and extract UserDetails
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();

            // Check if principal is UserDetails (not anonymous)
            if (principal instanceof UserDetails) {
                return (UserDetails) principal;
            }
        }

        // Return null or handle unauthenticated/anonymous users
        return null;
    }
}

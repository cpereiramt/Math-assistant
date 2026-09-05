package com.claySoftware.MathExpAssistant.exceptions;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("user profile not found");
    }
}

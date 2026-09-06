package com.claySoftware.MathExpAssistant.exceptions;

public class SocialAccessDeniedException extends RuntimeException {
    public SocialAccessDeniedException(String message) {
        super(message);
    }
}
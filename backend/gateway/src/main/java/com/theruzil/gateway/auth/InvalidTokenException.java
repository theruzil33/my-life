package com.theruzil.gateway.auth;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(Throwable cause) {
        super("Invalid or expired access token", cause);
    }

    public InvalidTokenException(String message) {
        super(message);
    }
}

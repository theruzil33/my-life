package com.theruzil.gateway.auth;

public class InvalidRegistrationDataException extends RuntimeException {

    public InvalidRegistrationDataException(String message) {
        super(message);
    }
}

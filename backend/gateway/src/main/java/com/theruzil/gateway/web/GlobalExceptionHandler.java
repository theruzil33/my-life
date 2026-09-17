package com.theruzil.gateway.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.theruzil.gateway.auth.EmailAlreadyUsedException;
import com.theruzil.gateway.auth.InvalidCredentialsException;
import com.theruzil.gateway.auth.InvalidRegistrationDataException;
import com.theruzil.gateway.auth.InvalidTokenException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(InvalidRegistrationDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidRegistrationData(InvalidRegistrationDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(InvalidTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError handleInvalidToken(InvalidTokenException ex) {
        return new ApiError(ex.getMessage());
    }
}

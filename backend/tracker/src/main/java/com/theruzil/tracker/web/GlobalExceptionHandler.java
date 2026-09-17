package com.theruzil.tracker.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.theruzil.tracker.habit.HabitNotFoundException;
import com.theruzil.tracker.habit.InvalidHabitDataException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidHabitDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidHabitData(InvalidHabitDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(HabitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleHabitNotFound(HabitNotFoundException ex) {
        return new ApiError(ex.getMessage());
    }
}

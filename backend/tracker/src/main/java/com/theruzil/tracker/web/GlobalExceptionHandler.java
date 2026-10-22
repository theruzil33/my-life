package com.theruzil.tracker.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.theruzil.tracker.habit.HabitNotFoundException;
import com.theruzil.tracker.habit.InvalidHabitDataException;
import com.theruzil.tracker.importing.InvalidImportDataException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidHabitDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidHabitData(InvalidHabitDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(InvalidImportDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidImportData(InvalidImportDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleUnreadableBody(HttpMessageNotReadableException ex) {
        return new ApiError("Invalid request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return new ApiError("Invalid request parameter: " + ex.getName());
    }

    @ExceptionHandler(HabitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleHabitNotFound(HabitNotFoundException ex) {
        return new ApiError(ex.getMessage());
    }
}

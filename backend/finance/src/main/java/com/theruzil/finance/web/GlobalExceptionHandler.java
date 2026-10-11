package com.theruzil.finance.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.theruzil.finance.balance.InvalidBalanceDataException;
import com.theruzil.finance.category.CategoryInUseException;
import com.theruzil.finance.category.CategoryNotFoundException;
import com.theruzil.finance.category.InvalidCategoryDataException;
import com.theruzil.finance.operation.InvalidOperationDataException;
import com.theruzil.finance.operation.OperationNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidBalanceDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidBalanceData(InvalidBalanceDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(InvalidCategoryDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidCategoryData(InvalidCategoryDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleCategoryNotFound(CategoryNotFoundException ex) {
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

    @ExceptionHandler(InvalidOperationDataException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalidOperationData(InvalidOperationDataException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(OperationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleOperationNotFound(OperationNotFoundException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(CategoryInUseException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleCategoryInUse(CategoryInUseException ex) {
        return new ApiError(ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return new ApiError("Request conflicts with existing data");
    }
}

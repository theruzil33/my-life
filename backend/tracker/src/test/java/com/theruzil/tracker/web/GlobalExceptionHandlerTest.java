package com.theruzil.tracker.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.theruzil.tracker.habit.HabitNotFoundException;
import com.theruzil.tracker.habit.InvalidHabitDataException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvalidHabitDataToBadRequestWithMessage() throws Exception {
        ApiError error = handler.handleInvalidHabitData(new InvalidHabitDataException("bad data"));

        assertThat(error.message()).isEqualTo("bad data");
        assertThat(statusOf("handleInvalidHabitData", InvalidHabitDataException.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsHabitNotFoundToNotFoundWithMessage() throws Exception {
        ApiError error = handler.handleHabitNotFound(new HabitNotFoundException("Habit not found"));

        assertThat(error.message()).isEqualTo("Habit not found");
        assertThat(statusOf("handleHabitNotFound", HabitNotFoundException.class)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpStatus statusOf(String methodName, Class<? extends RuntimeException> exceptionType)
            throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod(methodName, exceptionType);
        return method.getAnnotation(ResponseStatus.class).value();
    }
}

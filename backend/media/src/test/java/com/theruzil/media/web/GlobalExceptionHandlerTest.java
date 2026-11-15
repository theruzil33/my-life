package com.theruzil.media.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.theruzil.media.mediaitem.InvalidMediaDataException;
import com.theruzil.media.mediaitem.MediaItemNotFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsInvalidMediaDataToBadRequestWithMessage() throws Exception {
        ApiError error = handler.handleInvalidMediaData(new InvalidMediaDataException("bad data"));

        assertThat(error.message()).isEqualTo("bad data");
        assertThat(statusOf("handleInvalidMediaData", InvalidMediaDataException.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsMediaItemNotFoundToNotFoundWithMessage() throws Exception {
        ApiError error = handler.handleMediaItemNotFound(new MediaItemNotFoundException("Media item not found"));

        assertThat(error.message()).isEqualTo("Media item not found");
        assertThat(statusOf("handleMediaItemNotFound", MediaItemNotFoundException.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpStatus statusOf(String methodName, Class<? extends RuntimeException> exceptionType) throws Exception {
        Method method = GlobalExceptionHandler.class.getMethod(methodName, exceptionType);
        return method.getAnnotation(ResponseStatus.class).value();
    }
}

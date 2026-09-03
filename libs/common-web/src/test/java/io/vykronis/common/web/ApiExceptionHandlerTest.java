package io.vykronis.common.web;

import io.vykronis.common.api.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private static final String NAME = "env";

    @Test
    void onValidationReturns400CodeAndMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);

        ResponseEntity<ApiError> response = handler.onValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiError body = response.getBody();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.message()).isEqualTo("Invalid request body");
        assertThat(body.status()).isEqualTo(400);
    }

    @Test
    void onTypeMismatchReturns400WithArgumentName() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn(NAME);

        ResponseEntity<ApiError> response = handler.onTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Invalid value for '" + NAME + "'");
    }

    @Test
    void onNoResourceReturns404() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing");

        ResponseEntity<ApiError> response = handler.onNoResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void onIllegalArgumentReturns400WithOriginalMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("bad input");

        ResponseEntity<ApiError> response = handler.onIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("bad input");
    }

    @Test
    void onUnexpectedReturns500() {
        ResponseEntity<ApiError> response = handler.onUnexpected(new RuntimeException("boom"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().status()).isEqualTo(500);
    }
}

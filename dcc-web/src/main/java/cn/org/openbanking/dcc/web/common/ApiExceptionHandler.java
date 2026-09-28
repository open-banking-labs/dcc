package cn.org.openbanking.dcc.web.common;

import java.time.Instant;

import cn.org.openbanking.dcc.core.common.error.ConflictException;
import cn.org.openbanking.dcc.core.common.error.DomainException;
import cn.org.openbanking.dcc.core.common.error.ResourceNotFoundException;
import cn.org.openbanking.dcc.core.common.error.ValidationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain failures into HTTP responses in one place, so controllers stay
 * free of error handling and the domain stays free of transport types.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Stable error envelope returned for every handled failure. */
    public record ApiError(Instant timestamp, int status, String error, String message) {
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> invalid(ValidationException ex) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + " " + field.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("invalid request");
        return body(HttpStatus.BAD_REQUEST, "validation_error", message);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, DomainException ex) {
        return body(status, status.name().toLowerCase(), ex.getMessage());
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), error, message));
    }
}

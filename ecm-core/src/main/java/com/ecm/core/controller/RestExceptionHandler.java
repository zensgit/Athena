package com.ecm.core.controller;

import com.ecm.core.exception.AccessDeniedException;
import com.ecm.core.exception.DuplicateResourceException;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ModelValidationException;
import com.ecm.core.exception.NodeNotFoundException;
import com.ecm.core.exception.PropertyValidationException;
import com.ecm.core.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Maps common exceptions to consistent HTTP responses to avoid 500s for
 * predictable user errors (e.g., duplicate folder names).
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        log.debug("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.debug("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ModelValidationException.class)
    public ResponseEntity<ApiError> handleModelValidation(ModelValidationException ex, HttpServletRequest request) {
        log.debug("Model validation failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex.getViolations());
    }

    @ExceptionHandler(PropertyValidationException.class)
    public ResponseEntity<ApiError> handlePropertyValidation(PropertyValidationException ex, HttpServletRequest request) {
        log.debug("Property validation failed: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex.getViolations());
    }

    @ExceptionHandler({DuplicateResourceException.class, IllegalOperationException.class})
    public ResponseEntity<ApiError> handleDomainBadRequest(RuntimeException ex, HttpServletRequest request) {
        log.debug("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiError> handleForbidden(SecurityException ex, HttpServletRequest request) {
        log.debug("Forbidden: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Forbidden: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        log.debug("Not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({ResourceNotFoundException.class, NodeNotFoundException.class})
    public ResponseEntity<ApiError> handleDomainNotFound(RuntimeException ex, HttpServletRequest request) {
        log.debug("Not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        return build(status, message, request, null);
    }

    private ResponseEntity<ApiError> build(
        HttpStatus status,
        String message,
        HttpServletRequest request,
        List<String> details
    ) {
        ApiError body = new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            details
        );
        return ResponseEntity.status(status).body(body);
    }

    @Value
    private static class ApiError {
        Instant timestamp;
        int status;
        String error;
        String message;
        String path;
        List<String> details;
    }
}

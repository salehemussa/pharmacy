package com.pharmacy.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Some fields are invalid.",
                fields
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(error(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_REQUEST",
                "The request body could not be read.",
                null
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action.",
                null
        ));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> credentials(BadCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "Invalid username or password.",
                null
        ));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> disabled(DisabledException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error(
                HttpStatus.UNAUTHORIZED,
                "ACCOUNT_DEACTIVATED",
                "This account is deactivated. Contact an administrator.",
                null
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        String message = "The request conflicts with existing data.";
        String cause = exception.getMostSpecificCause().getMessage() == null
                ? ""
                : exception.getMostSpecificCause().getMessage().toLowerCase();
        if (cause.contains("barcode")) {
            message = "That barcode is already used by another medicine.";
        } else if (cause.contains("uq_batch") || cause.contains("batch_number")) {
            message = "That batch number already exists for this medicine.";
        } else if (cause.contains("username")) {
            message = "That username is already in use.";
        } else if (cause.contains("email")) {
            message = "That email address is already in use.";
        } else if (cause.contains("chk_") || cause.contains("check")) {
            message = "The request violates a data rule and was rejected.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                message,
                null
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("Unhandled error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Something went wrong. Please try again or contact an administrator.",
                null
        ));
    }

    private ApiError error(HttpStatus status, String code, String message, Map<String, String> fields) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, code, fields);
    }
}

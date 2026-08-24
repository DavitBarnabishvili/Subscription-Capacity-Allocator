package com.arcticblu.subscriptioncapacityallocator.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> inputValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors();

        List<String> distinctMessages = fieldErrors.stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .toList();

        String result = distinctMessages.stream()
                .map(message -> formatValidationError(fieldErrors, message))
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(new ErrorResponse(result));
    }

    @ExceptionHandler(SubscriptionRunNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(SubscriptionRunNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> illegalArgumentHandler(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> malformedRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse("Malformed request body"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getName() + " must be a valid UUID"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> catchAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("An unexpected error occurred"));
    }


    private String formatValidationError(List<FieldError> fieldErrors, String message) {
        String fields = fieldErrors.stream()
                .filter(error -> Objects.equals(error.getDefaultMessage(), message))
                .map(this::simpleFieldName)
                .distinct()
                .collect(Collectors.joining(", "));

        return fields + ": " + message;
    }


    //some of the fields were getting presented as, for example,
    // "availableSubscriptions[0].requestedAmount" instead of just "requestedAmount"
    // this method helps with error readability.
    private String simpleFieldName(FieldError error) {
        String field = error.getField();
        int lastDot = field.lastIndexOf('.');
        return lastDot == -1 ? field : field.substring(lastDot + 1);
    }
}
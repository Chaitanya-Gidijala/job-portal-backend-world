package com.job.portal.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        Map<String, String> errorMessages = new HashMap<>();

        for (FieldError error : result.getFieldErrors()) {
            errorMessages.put(error.getField(), error.getDefaultMessage());
        }
        return new ResponseEntity<>(errorMessages, HttpStatus.BAD_REQUEST); // Return 400 status
    }

    // Handle JobNotFoundException
    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<String> handleJobNotFoundException(JobNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // Handle ResourceNotFoundException
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // Handle JobAlreadyExistsException
    @ExceptionHandler(JobAlreadyExistsException.class)
    public ResponseEntity<String> handleJobAlreadyExistsException(JobAlreadyExistsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // Handle AccessDeniedException
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException ex) {
        return new ResponseEntity<>("Access Denied: You do not have the required role to access this resource.",
                HttpStatus.FORBIDDEN);
    }

    // Keep explicit HTTP statuses (404 / 503 …) instead of collapsing them into 500
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(org.springframework.web.server.ResponseStatusException ex) {
        return new ResponseEntity<>(ex.getReason(), ex.getStatusCode());
    }

    // A Server-Sent Events stream reached its timeout — the browser reconnects on its own
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout() {
        return ResponseEntity.noContent().build();
    }

    // Unknown URLs (bots, typos) are 404s — not server errors that inflate the error rate
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<com.job.portal.dto.ApiResponse<Void>> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return new ResponseEntity<>(com.job.portal.dto.ApiResponse.error("Not found", null), HttpStatus.NOT_FOUND);
    }

    // Handle all other exceptions — logged with the stack trace so production failures are diagnosable
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).error("Unhandled exception", ex);
        // Return the root cause (e.g. the actual database error), not a long framework wrapper.
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return new ResponseEntity<>(message.length() > 500 ? message.substring(0, 500) : message, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

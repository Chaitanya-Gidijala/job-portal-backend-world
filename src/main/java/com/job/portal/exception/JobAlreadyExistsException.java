package com.job.portal.exception;

public class JobAlreadyExistsException extends RuntimeException {
    public JobAlreadyExistsException(String message) {
        super(message);
    }
}

package com.ecm.core.exception;

public class IllegalOperationException extends RuntimeException {
    public IllegalOperationException(String message) {
        super(message);
    }

    public IllegalOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

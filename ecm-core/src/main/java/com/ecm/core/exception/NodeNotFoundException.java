package com.ecm.core.exception;

public class NodeNotFoundException extends ResourceNotFoundException {
    public NodeNotFoundException(String message) {
        super(message);
    }

    public NodeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

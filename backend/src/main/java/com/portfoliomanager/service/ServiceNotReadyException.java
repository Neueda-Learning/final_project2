package com.portfoliomanager.service;

public class ServiceNotReadyException extends RuntimeException {

    public ServiceNotReadyException(String message) {
        super(message);
    }

    public ServiceNotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
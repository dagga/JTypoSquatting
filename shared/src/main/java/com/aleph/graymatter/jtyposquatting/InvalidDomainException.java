package com.aleph.graymatter.jtyposquatting;

public class InvalidDomainException extends Exception {
    public InvalidDomainException(String message) {
        super(message);
    }

    public InvalidDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.tewendelabs.airag.exceptions;

public class DemoQuotaExceededException extends RuntimeException {

    public DemoQuotaExceededException(String message) {
        super(message);
    }
}

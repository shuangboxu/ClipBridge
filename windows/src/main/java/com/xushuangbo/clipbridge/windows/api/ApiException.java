package com.xushuangbo.clipbridge.windows.api;

public class ApiException extends RuntimeException {
    private final int statusCode;

    public ApiException(String message) {
        this(0, message, null);
    }

    public ApiException(String message, Throwable cause) {
        this(0, message, cause);
    }

    public ApiException(int statusCode, String message) {
        this(statusCode, message, null);
    }

    public ApiException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }
}

package com.theminespirit.core;

public final class ErrorText {
    private ErrorText() {}

    public static String of(Throwable error) {
        String message = error.getMessage();
        return message != null ? message : error.getClass().getSimpleName();
    }
}

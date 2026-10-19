package com.theminespirit.core.tactics;

import java.util.List;

public final class InvalidFieldException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String owner;
    private final String path;
    private final String reason;

    public InvalidFieldException(String field, String message) {
        super(message);
        this.reason = message;
        int dot = field.indexOf('.');
        this.owner = dot < 0 ? "" : field.substring(0, dot);
        this.path = field.substring(dot + 1);
    }

    @Override
    public String getMessage() {
        return reason;
    }

    String owner() {
        return owner;
    }

    List<String> path() {
        return List.of(path.split("[.]"));
    }
}

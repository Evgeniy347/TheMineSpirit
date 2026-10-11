package com.theminespirit.core.tactics;

final class InvalidFieldException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String owner;
    private final String name;

    InvalidFieldException(String field, String message) {
        super(message);
        int dot = field.lastIndexOf('.');
        this.owner = dot < 0 ? "" : field.substring(0, dot);
        this.name = field.substring(dot + 1);
    }

    String owner() {
        return owner;
    }

    String fieldName() {
        return name;
    }
}

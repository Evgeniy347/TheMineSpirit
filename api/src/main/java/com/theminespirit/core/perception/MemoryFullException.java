package com.theminespirit.core.perception;

public final class MemoryFullException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final MemoryType type;

    public MemoryFullException(MemoryType type, int limit) {
        super("память типа " + type.id() + " заполнена (" + limit + " точек): забудь точку этого типа, "
                + "прежде чем запоминать новую");
        this.type = type;
    }

    public MemoryType type() {
        return type;
    }
}

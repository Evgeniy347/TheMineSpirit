package com.theminespirit.core.task;

import java.util.Locale;

public enum TaskState {
    ACCEPTED("accepted", false),
    RUNNING("running", false),
    SUCCEEDED("succeeded", true),
    FAILED("failed", true),
    CANCELLED("cancelled", true);

    private final String wire;
    private final boolean terminal;

    TaskState(String wire, boolean terminal) {
        this.wire = wire;
        this.terminal = terminal;
    }

    public String wire() {
        return wire;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean canTransitionTo(TaskState next) {
        if (next == null) {
            throw new IllegalArgumentException("Следующее состояние задачи не может быть null: переход выбирают явно");
        }
        if (this == next) {
            return true;
        }
        return switch (this) {
            case ACCEPTED -> true;
            case RUNNING -> next == SUCCEEDED || next == FAILED || next == CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }

    public static TaskState fromWire(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Состояние задачи не может быть null: клиент передаёт wire-строку");
        }
        for (TaskState state : values()) {
            if (state.wire.equals(text.toLowerCase(Locale.ROOT))) {
                return state;
            }
        }
        throw new IllegalArgumentException("Неизвестное состояние задачи '" + text + "': допустимы accepted, running, "
                + "succeeded, failed, cancelled");
    }
}

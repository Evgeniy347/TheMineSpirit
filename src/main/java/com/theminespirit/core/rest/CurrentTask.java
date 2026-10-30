package com.theminespirit.core.rest;

import org.jspecify.annotations.Nullable;

public final class CurrentTask {
    private CurrentTask() {}

    public static @Nullable String of(
            @Nullable String restTask,
            @Nullable String boardTaskId,
            @Nullable String activity,
            @Nullable String script) {
        if (named(restTask)) {
            return restTask;
        }
        if (named(boardTaskId)) {
            return boardTaskId;
        }
        if (named(activity)) {
            return activity;
        }
        return named(script) ? script : null;
    }

    private static boolean named(@Nullable String text) {
        return text != null && !text.isBlank();
    }
}

package com.theminespirit.core.board;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class DispatchBusy {
    public enum Mode {
        IDLE,
        WORK,
        TACTICS,
        BACKGROUND
    }

    private DispatchBusy() {}

    public static @Nullable String text(@Nullable String script, @Nullable TaskNode plan, Mode mode) {
        Objects.requireNonNull(mode, "Режим занятости не может быть null: по нему решают, что писать в причине");
        if (mode == Mode.IDLE || mode == Mode.BACKGROUND) {
            return null;
        }
        String shown = script;
        if (shown == null && plan != null) {
            shown = BoardActivities.firstScript(plan);
        }
        if (shown == null) {
            shown = "задача";
        }
        return mode == Mode.TACTICS ? "тактика держит (" + shown + ")" : shown;
    }
}

package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class StateChangeLog {
    private final Map<String, String> states = new HashMap<>();

    public boolean changed(String channel, String state) {
        Objects.requireNonNull(channel, "Канал журнала не может быть null: по нему решается, что уже писали");
        Objects.requireNonNull(state, "Состояние не может быть null: без него не понять, изменилось ли что-то");
        String before = states.get(channel);
        if (state.equals(before)) {
            return false;
        }
        states.put(channel, state);
        return true;
    }

    public void forget(String channel) {
        Objects.requireNonNull(channel, "Канал журнала не может быть null: забывать нечего");
        states.remove(channel);
    }
}

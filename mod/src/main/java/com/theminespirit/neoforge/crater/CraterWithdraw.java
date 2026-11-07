package com.theminespirit.neoforge.crater;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class CraterWithdraw {
    static final String DONE = "withdraw_done";

    private boolean planned;
    private boolean done;

    boolean needsStart() {
        return !planned;
    }

    void started() {
        planned = true;
    }

    void finished() {
        done = true;
    }

    Map<String, Object> save(Map<String, Object> session) {
        Objects.requireNonNull(session, "Состояние засыпки не может быть null: без него нечего дополнять");
        Map<String, Object> state = new LinkedHashMap<>(session);
        state.put(DONE, done);
        return Map.copyOf(state);
    }

    void restore(Map<String, Object> state) {
        Objects.requireNonNull(state, "Сохранённое состояние не может быть null: без него нечего восстанавливать");
        done = Boolean.TRUE.equals(state.get(DONE));
        planned = done;
    }
}

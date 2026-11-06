package com.theminespirit.core.board;

import java.util.Objects;

public final class SubmitGate {
    private final StateChangeLog conditions = new StateChangeLog();

    public boolean allow(String scope, String signature) {
        Objects.requireNonNull(scope, "Область не может быть null: по ней разные задачи не глушат друг друга");
        Objects.requireNonNull(
                signature, "Подпись условий не может быть null: без неё не понять, изменилось ли что-то");
        return conditions.changed(scope, signature);
    }

    public void forget(String scope) {
        Objects.requireNonNull(scope, "Область не может быть null: забывать нечего");
        conditions.forget(scope);
    }
}

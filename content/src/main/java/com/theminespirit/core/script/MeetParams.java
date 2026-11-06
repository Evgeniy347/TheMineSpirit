package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record MeetParams(String target) {
    public MeetParams {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Не задана цель meet: пустую строку не сопоставить ни с одним игроком "
                    + "или духом, а с кем встретиться — не из чего");
        }
        PersonSelector.requirePerson(target, "target");
    }

    public static MeetParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new MeetParams(ScriptArgs.requireString(params, "target"));
    }
}

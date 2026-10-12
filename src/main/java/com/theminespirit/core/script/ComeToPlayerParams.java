package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;

public record ComeToPlayerParams(String player) {
    public ComeToPlayerParams {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException(
                    "Не задан игрок для come_to_player: без имени скрипт идёт к ближайшему, а пустое имя не сопоставить ни с кем");
        }
    }

    public static ComeToPlayerParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        return new ComeToPlayerParams(ScriptArgs.requireString(params, "player"));
    }
}

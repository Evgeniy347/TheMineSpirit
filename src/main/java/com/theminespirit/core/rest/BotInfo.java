package com.theminespirit.core.rest;

import java.util.Objects;

public record BotInfo(String id, String name, BotState state, int x, int y, int z, String dimension,
        String currentTask) {
    public BotInfo {
        Objects.requireNonNull(id, "id не может быть null: по id клиент обращается к духу");
        Objects.requireNonNull(name, "name не может быть null: имя показывается в списке духов");
        Objects.requireNonNull(state, "state не может быть null: клиент выбирает действия по состоянию духа");
        Objects.requireNonNull(dimension, "dimension не может быть null: позиция без измерения неоднозначна");
    }
}

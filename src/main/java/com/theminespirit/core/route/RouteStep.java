package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record RouteStep(Action action, BlockCoord cell) {
    public enum Action {
        WALK,
        SWIM,
        BREAK,
        PLACE,
        PILLAR
    }

    public RouteStep {
        Objects.requireNonNull(action, "Действие шага не может быть null: без него дух не знает, что делать");
        Objects.requireNonNull(cell, "Клетка шага не может быть null: без неё неизвестно, где действовать");
    }
}

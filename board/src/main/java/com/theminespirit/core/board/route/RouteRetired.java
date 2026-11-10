package com.theminespirit.core.board.route;

import java.util.Objects;

public record RouteRetired(long id, RouteResource resource, RouteTrail trail) {
    public RouteRetired {
        if (id < 0) {
            throw new IllegalArgumentException("Номер снятия " + id + ": он не отрицателен, иначе задание не опознать");
        }
        Objects.requireNonNull(resource, "Ресурс снимаемой тропы не может быть null: по нему ищут маршрут");
        Objects.requireNonNull(trail, "Снимаемая тропа не может быть null: без клеток нечего убирать");
        if (trail.empty()) {
            throw new IllegalArgumentException("Снятие " + id + " без клеток: пустую тропу не убирают");
        }
    }
}

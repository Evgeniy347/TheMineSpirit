package com.theminespirit.core.base;

import com.theminespirit.core.tactics.Point;

public record Camp(Point center, double radius, int owner, long setAt) {
    public Camp {
        if (center == null) {
            throw new IllegalArgumentException("Центр лагеря не может быть null: без точки лагерь некуда ставить");
        }
        if (!Double.isFinite(radius) || radius <= 0) {
            throw new IllegalArgumentException(
                    "Радиус лагеря = " + radius + ": спокойная зона обязана быть конечной и больше нуля");
        }
        if (owner < 0) {
            throw new IllegalArgumentException(
                    "Владелец лагеря = " + owner + ": id семьи/духа не может быть отрицательным, нет владельца — 0");
        }
        if (setAt < 0) {
            throw new IllegalArgumentException(
                    "Время постановки лагеря = " + setAt + ": тик установки не может быть отрицательным");
        }
    }

    public boolean contains(Point point) {
        if (point == null) {
            throw new IllegalArgumentException("Проверка лагеря без точки не считается: пустой точке негде быть");
        }
        return center.distanceTo(point) <= radius;
    }
}

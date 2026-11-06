package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RoutePlan {
    public enum Mode {
        LOOP,
        PING_PONG
    }

    private final List<BlockCoord> points;
    private final Mode mode;
    private boolean forward = true;

    public RoutePlan(List<BlockCoord> points, Mode mode) {
        Objects.requireNonNull(points, "Точки маршрута не могут быть null: без них патрулю некуда идти");
        Objects.requireNonNull(
                mode, "Режим маршрута не может быть null: без него неизвестно, кругом идти или туда-обратно");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Маршрут без точек: патрулю некуда идти, дух встанет до первого шага");
        }
        this.points = List.copyOf(points);
        this.mode = mode;
    }

    public List<BlockCoord> points() {
        return points;
    }

    public Mode mode() {
        return mode;
    }

    public Optional<Integer> next(int current, Set<Integer> skipped) {
        Objects.requireNonNull(skipped, "Множество пропущенных не может быть null: без него неизвестно, что обходить");
        if (current < 0 || current >= points.size()) {
            throw new IndexOutOfBoundsException("Текущая точка " + current + " вне маршрута из " + points.size()
                    + ": патруль стоит не на своей точке, проверьте счётчик маршрута");
        }
        List<Integer> reachable = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (!skipped.contains(i)) {
                reachable.add(i);
            }
        }
        if (reachable.isEmpty()) {
            return Optional.empty();
        }
        if (reachable.size() == 1) {
            return Optional.of(reachable.get(0));
        }
        if (mode == Mode.LOOP) {
            int i = current;
            do {
                i = (i + 1) % points.size();
            } while (skipped.contains(i));
            return Optional.of(i);
        }
        if (current == 0) {
            forward = true;
        }
        if (current == points.size() - 1) {
            forward = false;
        }
        int step = forward ? 1 : -1;
        int i = current;
        for (int guard = 0; guard < 2 * points.size(); guard++) {
            i += step;
            if (i < 0 || i >= points.size()) {
                step = -step;
                i = current + step;
            }
            if (!skipped.contains(i)) {
                forward = step > 0;
                return Optional.of(i);
            }
        }
        return Optional.of(reachable.get(0));
    }
}

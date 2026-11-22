package com.theminespirit.core.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MapDock {
    private MapDock() {}

    public record Stats(int done, int started, int empty, int damaged) {
        public Stats {
            if (done < 0 || started < 0 || empty < 0 || damaged < 0) {
                throw new IllegalArgumentException("Счётчики участков " + done + "/" + started + "/" + empty + "/"
                        + damaged + " отрицательны: статистике нечего показывать");
            }
        }

        public int total() {
            return done + started + empty + damaged;
        }
    }

    public static Stats stats(List<TownMapView.MapRect> rects) {
        Objects.requireNonNull(rects, "Прямоугольники плана не могут быть null: пустой план — пустой список");
        int done = 0;
        int started = 0;
        int empty = 0;
        int damaged = 0;
        for (TownMapView.MapRect rect : rects) {
            if (rect.kind() != TownMapView.Kind.LOT) {
                continue;
            }
            if (rect.damaged()) {
                damaged++;
            } else if (rect.percent() >= 100) {
                done++;
            } else if (rect.percent() > 0) {
                started++;
            } else {
                empty++;
            }
        }
        return new Stats(done, started, empty, damaged);
    }

    public static List<TownMapView.MapRect> nearby(
            List<TownMapView.MapRect> rects, double centerX, double centerZ, int limit) {
        Objects.requireNonNull(rects, "Прямоугольники плана не могут быть null: пустой план — пустой список");
        if (limit < 0) {
            throw new IllegalArgumentException("Лимит ближайших участков " + limit + " отрицателен");
        }
        return rects.stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.LOT)
                .sorted(Comparator.comparingDouble(rect -> distance(rect, centerX, centerZ)))
                .limit(limit)
                .toList();
    }

    static double distance(TownMapView.MapRect rect, double x, double z) {
        double cx = (rect.x1() + rect.x2() + 1) / 2.0;
        double cz = (rect.z1() + rect.z2() + 1) / 2.0;
        return Math.hypot(cx - x, cz - z);
    }
}

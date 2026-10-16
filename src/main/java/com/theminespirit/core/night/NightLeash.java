package com.theminespirit.core.night;

import com.theminespirit.core.doors.DoorTuning;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public final class NightLeash {
    public static final String OUTSIDE_SETTLEMENT_REASON = "ночь: за границу поселения не выходим";

    private final DoorTuning.DoorRules rules;

    public NightLeash(DoorTuning.DoorRules rules) {
        this.rules = Objects.requireNonNull(rules,
                "Ночной поводок без правил не работает: порог вечера и радиус поселения живут в doors.json");
    }

    public static NightLeash withDefaultRules() {
        return new NightLeash(DoorTuning.loadDefault());
    }

    public int settlementRadius() {
        return rules.settlementRadius();
    }

    public boolean isEvening(double dayFraction) {
        if (Double.isNaN(dayFraction) || dayFraction < 0.0 || dayFraction > 1.0) {
            throw new IllegalArgumentException(
                    "Доля суток = " + dayFraction + ": время суток обязано лежать в 0..1");
        }
        return dayFraction >= rules.eveningDayFraction();
    }

    public boolean insideSettlement(BlockCoord position, BlockCoord home, int radius) {
        Objects.requireNonNull(position, "Позиция не может быть null: принадлежность считается от координат");
        Objects.requireNonNull(home, "Дом не может быть null: поселение строится вокруг дома духа");
        if (radius < 1) {
            throw new IllegalArgumentException(
                    "Радиус поселения " + radius + " меньше 1: с ним дух не удержал бы ни одной точки");
        }
        return distance(position, home) <= radius;
    }

    public boolean armedNext(boolean evening, boolean armed, boolean insideSettlement) {
        return evening && (armed || insideSettlement);
    }

    public Decision checkTarget(BlockCoord target, BlockCoord home, int radius, boolean evening,
            boolean insideSinceEvening, boolean followingPlayer) {
        Objects.requireNonNull(target, "Цель не может быть null: проверяется, лежит ли она в поселении");
        if (!evening || !insideSinceEvening || followingPlayer || home == null) {
            return new Allowed();
        }
        if (insideSettlement(target, home, radius)) {
            return new Allowed();
        }
        return new Refused(OUTSIDE_SETTLEMENT_REASON);
    }

    public sealed interface Decision {
    }

    public record Allowed() implements Decision {
    }

    public record Refused(String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }

    private static double distance(BlockCoord first, BlockCoord second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}

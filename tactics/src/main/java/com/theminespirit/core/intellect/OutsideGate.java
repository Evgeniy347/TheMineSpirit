package com.theminespirit.core.intellect;

import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.tactics.Point;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class OutsideGate {
    public static final String NIGHT_REASON = "ночь: внешняя работа не назначается";

    private final OutsideRules rules;
    private final int settlementRadius;
    private final NightLeash leash;

    public OutsideGate(OutsideRules rules, int settlementRadius) {
        this.rules = Objects.requireNonNull(
                rules, "Правило выхода без данных не работает: порог группы и радиус живут в outside.json");
        if (settlementRadius < 1) {
            throw new IllegalArgumentException(
                    "Радиус поселения " + settlementRadius + " меньше 1: с ним ни одна точка не считалась бы своей");
        }
        this.settlementRadius = settlementRadius;
        this.leash = NightLeash.withDefaultRules();
    }

    public static OutsideGate withDefaults() {
        return new OutsideGate(
                OutsideRules.loadDefault(), NightLeash.withDefaultRules().settlementRadius());
    }

    public int partyMin() {
        return rules.partyMin();
    }

    public int settlementRadius() {
        return settlementRadius;
    }

    public boolean isEvening(double dayFraction) {
        return leash.isEvening(dayFraction);
    }

    public Decision admitExternal(int party) {
        return decide(party, false, "");
    }

    public Decision admitExternal(int party, boolean evening) {
        return decide(party, evening, "");
    }

    public Decision admitExternal(int party, boolean evening, @Nullable Point target, @Nullable Point home) {
        String boundary = boundary(target, home);
        if (target != null && home != null && distance(target, home) <= settlementRadius) {
            return new Inside("цель " + coordinates(target) + boundary + ": внешней работой не считается");
        }
        return decide(party, evening, boundary);
    }

    private String boundary(@Nullable Point target, @Nullable Point home) {
        if (target == null || home == null) {
            return " (граница поселения: радиус " + settlementRadius + ", цель неизвестна — считать нечем)";
        }
        double distance = distance(target, home);
        return " (граница поселения: радиус " + settlementRadius + ", цель " + coordinates(target) + " в "
                + Math.round(distance * 10) / 10.0 + " блоках от дома " + coordinates(home)
                + (distance <= settlementRadius ? "" : ": цель за границей") + ")";
    }

    private Decision decide(int party, boolean evening, String boundary) {
        requireParty(party);
        if (evening) {
            return new Refused(NIGHT_REASON + boundary);
        }
        if (party >= rules.partyMin()) {
            return new Allowed();
        }
        return new Refused(
                "за границей поселения в одиночку не ходят: нужна группа от " + rules.partyMin() + " духов" + boundary);
    }

    private static double distance(Point first, Point second) {
        double dx = first.x() - second.x();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String coordinates(Point point) {
        return point.x() + " " + point.y() + " " + point.z();
    }

    private static void requireParty(int party) {
        if (party < 0) {
            throw new IllegalArgumentException("Группа " + party + ": число духов не может быть отрицательным");
        }
    }

    public sealed interface Decision {}

    public record Allowed() implements Decision {}

    public record Inside(String note) implements Decision {
        public Inside {
            Objects.requireNonNull(
                    note, "Запись о работе внутри поселения обязана называть границу: по ней читают журнал");
        }
    }

    public record Refused(String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }
}

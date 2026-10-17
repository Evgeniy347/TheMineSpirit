package com.theminespirit.core.intellect;

import com.theminespirit.core.night.NightLeash;
import com.theminespirit.core.tactics.Point;
import java.util.Objects;

public final class OutsideGate {
    private final OutsideRules rules;
    private final int settlementRadius;

    public OutsideGate(OutsideRules rules, int settlementRadius) {
        this.rules = Objects.requireNonNull(rules,
                "Правило выхода без данных не работает: порог группы и радиус живут в outside.json");
        if (settlementRadius < 1) {
            throw new IllegalArgumentException("Радиус поселения " + settlementRadius
                    + " меньше 1: с ним ни одна точка не считалась бы своей");
        }
        this.settlementRadius = settlementRadius;
    }

    public static OutsideGate withDefaults() {
        return new OutsideGate(OutsideRules.loadDefault(), NightLeash.withDefaultRules().settlementRadius());
    }

    public int partyMin() {
        return rules.partyMin();
    }

    public int settlementRadius() {
        return settlementRadius;
    }

    public boolean outside(Point target, Point home) {
        Objects.requireNonNull(target, "Цель не может быть null: проверяется, лежит ли она за границей");
        Objects.requireNonNull(home, "Дом не может быть null: граница считается от дома поселения");
        return Math.sqrt(square(target.x() - home.x()) + square(target.y() - home.y()) + square(target.z() - home.z()))
                > settlementRadius;
    }

    public Decision admit(Point target, Point home, int party, boolean playerAccompanied) {
        requireParty(party);
        if (!outside(target, home)) {
            return new Allowed();
        }
        return admitExternal(party, playerAccompanied);
    }

    public Decision admitExternal(int party, boolean playerAccompanied) {
        requireParty(party);
        if (playerAccompanied || party >= rules.partyMin()) {
            return new Allowed();
        }
        return new Refused("за границей поселения в одиночку не ходят: нужна группа от " + rules.partyMin()
                + " духов или реальный игрок рядом");
    }

    private static void requireParty(int party) {
        if (party < 0) {
            throw new IllegalArgumentException("Группа " + party + ": число духов не может быть отрицательным");
        }
    }

    private static double square(double value) {
        return value * value;
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
}

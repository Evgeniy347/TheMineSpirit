package com.theminespirit.core.intellect;

import com.theminespirit.core.night.NightLeash;
import java.util.Objects;

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

    public double escortRadius() {
        return rules.escortRadius();
    }

    public boolean isEvening(double dayFraction) {
        return leash.isEvening(dayFraction);
    }

    public Decision admitExternal(int party, boolean playerAccompanied) {
        return admitExternal(party, playerAccompanied, false);
    }

    public Decision admitExternal(int party, boolean playerAccompanied, boolean evening) {
        requireParty(party);
        if (evening) {
            return new Refused(NIGHT_REASON);
        }
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

    public sealed interface Decision {}

    public record Allowed() implements Decision {}

    public record Refused(String reason) implements Decision {
        public Refused {
            Objects.requireNonNull(reason, "Причина отказа не может быть null: по ней строится итог задачи");
        }
    }
}

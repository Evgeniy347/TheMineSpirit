package com.theminespirit.core.policy;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class SettlementBreak {
    public enum Check {
        DEFAULT,
        OWN,
        SETTLEMENT
    }

    public enum Origin {
        PLAYER,
        SPIRIT,
        UNKNOWN
    }

    public enum Place {
        OFF_PLOT,
        PLOT,
        PLAN
    }

    public enum Intent {
        BREAK,
        DEMOLISH
    }

    private SettlementBreak() {}

    public static Place place(boolean onPlot, boolean inPlan) {
        if (inPlan) {
            return Place.PLAN;
        }
        return onPlot ? Place.PLOT : Place.OFF_PLOT;
    }

    public record Facts(
            Origin origin,
            boolean onPlot,
            boolean inPlan,
            String blockId,
            boolean inProtectedZone,
            boolean unprotectArea) {
        public Facts {
            Objects.requireNonNull(origin, "Происхождение блока не может быть null: от него зависит правило");
            Objects.requireNonNull(blockId, "Id блока не может быть null: неизвестно, что проверяем");
        }
    }

    public static BlockProtection.Decision decide(
            BlockProtection protection, Facts facts, Supplier<List<String>> neighbours) {
        return decide(protection, facts, Intent.BREAK, neighbours);
    }

    public static BlockProtection.Decision decide(
            BlockProtection protection, Facts facts, Intent intent, Supplier<List<String>> neighbours) {
        Objects.requireNonNull(protection, "Защита блоков не может быть null: без неё не решить, ломать ли");
        Objects.requireNonNull(facts, "Факты о блоке не могут быть null: без них не выбрать правило");
        Objects.requireNonNull(intent, "Намерение ломки не может быть null: от него зависит, трогать ли план");
        Objects.requireNonNull(neighbours, "Соседи не могут быть null: без них не отличить постройку");
        Place place = place(facts.onPlot(), facts.inPlan());
        if (place == Place.PLAN && intent == Intent.BREAK && facts.origin() == Origin.SPIRIT) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_BLOCKS,
                    "блок '" + facts.blockId() + "' в клетке плана постройки поселения: его не добывают и не копают, "
                            + "снимают только сносом по заданию (observe14: дух рубил стены хижины ради брёвен)");
        }
        return switch (of(facts.origin(), place)) {
            case SETTLEMENT -> protection.checkSettlement(facts.blockId(), facts.inProtectedZone());
            case OWN -> SettlementBreakProtection.own(protection, facts.blockId(), facts.inProtectedZone());
            case DEFAULT ->
                protection.check(
                        facts.blockId(),
                        neighbours.get(),
                        new BlockProtection.Placement(
                                facts.origin() == Origin.PLAYER, facts.inProtectedZone(), facts.unprotectArea()));
        };
    }

    public static Check of(Origin origin, Place place) {
        if (place == Place.PLAN) {
            return Check.OWN;
        }
        if (origin == Origin.PLAYER) {
            return Check.DEFAULT;
        }
        if (place == Place.PLOT) {
            return Check.SETTLEMENT;
        }
        return origin == Origin.SPIRIT ? Check.OWN : Check.DEFAULT;
    }
}

package com.theminespirit.core.policy;

import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlanBuildings;
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
            boolean unprotectArea,
            PlanBuilding planBuilding,
            PlanBuilding ownBuilding) {
        public Facts {
            Objects.requireNonNull(origin, "Происхождение блока не может быть null: от него зависит правило");
            Objects.requireNonNull(blockId, "Id блока не может быть null: неизвестно, что проверяем");
            Objects.requireNonNull(planBuilding, "Постройка клетки не названа: без неё не отличить чужую клетку плана");
            Objects.requireNonNull(ownBuilding, "Своя постройка не названа: без неё не отличить чужую клетку плана");
        }

        public Facts(
                Origin origin,
                boolean onPlot,
                boolean inPlan,
                String blockId,
                boolean inProtectedZone,
                boolean unprotectArea) {
            this(origin, onPlot, inPlan, blockId, inProtectedZone, unprotectArea, PlanBuilding.NONE, PlanBuilding.NONE);
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
        if (place == Place.PLAN
                && intent == Intent.BREAK
                && (facts.planBuilding() != PlanBuilding.NONE || facts.origin() == Origin.SPIRIT)) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_BLOCKS,
                    "блок '" + facts.blockId() + "' в клетке плана постройки поселения (" + facts.planBuilding()
                            + "): его не добывают и не копают, снимают только сносом по заданию "
                            + "(observe14: дух рубил стены хижины ради брёвен)");
        }
        if (place == Place.PLAN
                && intent == Intent.DEMOLISH
                && PlanBuildings.foreign(facts.ownBuilding(), facts.planBuilding())) {
            return new BlockProtection.Refused(
                    PolicyId.PROTECTED_BLOCKS,
                    "блок '" + facts.blockId() + "' принадлежит постройке " + facts.planBuilding()
                            + ", а задание строит " + facts.ownBuilding()
                            + ": чужую постройку плана не разбирают (observe23: забор снял стену хижины "
                            + "в (-178..-176,69,-505), дом 100→94)");
        }
        return switch (of(facts.origin(), place)) {
            case SETTLEMENT -> settlement(protection, facts, neighbours);
            case OWN -> SettlementBreakProtection.own(protection, facts.blockId(), facts.inProtectedZone());
            case DEFAULT ->
                protection.check(
                        facts.blockId(),
                        neighbours.get(),
                        new BlockProtection.Placement(
                                facts.origin() == Origin.PLAYER, facts.inProtectedZone(), facts.unprotectArea()));
        };
    }

    private static BlockProtection.Decision settlement(
            BlockProtection protection, Facts facts, Supplier<List<String>> neighbours) {
        BlockProtection.Decision mechanisms = protection.checkSettlement(facts.blockId(), facts.inProtectedZone());
        if (mechanisms instanceof BlockProtection.Refused) {
            return mechanisms;
        }
        return protection.check(
                facts.blockId(),
                neighbours.get(),
                new BlockProtection.Placement(
                        facts.origin() == Origin.PLAYER, facts.inProtectedZone(), facts.unprotectArea()));
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

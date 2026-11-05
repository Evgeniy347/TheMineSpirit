package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;

public final class SettlementLevel {
    public static final String RULE = "SettlementLevel.v1";

    private static final int FIRST_STAGE = 1;

    private SettlementLevel() {}

    public static int of(SettlementPlot plot) {
        Objects.requireNonNull(plot, "Участок не может быть null: без него у поселения нет высоты");
        return plot.anchor().y();
    }

    public static boolean marked(String rule) {
        return RULE.equals(rule);
    }

    public static Optional<StageSite> site(TownPlan plan, int stage, int level) {
        Objects.requireNonNull(plan, "План не может быть null: места этапа берутся из него");
        return StageSites.find(plan, stage, level, TownRules.current());
    }

    public static Optional<StageSite> site(TownPlan plan, int stage, SettlementPlot plot) {
        return site(plan, stage, of(plot));
    }

    public static Optional<ColumnMask> finalArea(TownPlan plan) {
        Objects.requireNonNull(plan, "План не может быть null: площадь финального этапа берётся из него");
        TownRules rules = TownRules.current();
        int stage = StageRules.current().stages().size();
        Optional<ColumnMask> area = StageSites.levelArea(plan, stage, rules);
        return area.isPresent() ? area : StageSites.levelArea(plan, FIRST_STAGE, rules);
    }

    public static SettlementPlot remeasure(
            SettlementPlot stored, ColumnMask area, IntBinaryOperator feet, int above, LevelingRules rules) {
        Objects.requireNonNull(stored, "Сохранённый участок не может быть null: мигрирует записанная высота");
        return survey(stored.anchor(), area, feet, stored.wood(), above, rules);
    }

    public static int choose(ColumnMask area, IntBinaryOperator feet, LevelingRules rules, int above) {
        Objects.requireNonNull(area, "Площадки не могут быть null: высоту выбирают по ним");
        Objects.requireNonNull(feet, "Высоты колонок не могут быть null: без них высоту не выбрать");
        Objects.requireNonNull(rules, "Правила выравнивания не могут быть null: запас берётся из данных");
        return PlotHeight.choose(area, feet, rules, Math.max(above, Integer.MIN_VALUE))
                .level();
    }

    public static SettlementPlot survey(
            BlockCoord center, ColumnMask area, IntBinaryOperator feet, String wood, int above, LevelingRules rules) {
        int level = choose(area, feet, rules, above);
        return SettlementPlot.survey(center, area, feet, wood, OptionalInt.of(level));
    }
}

package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;

public final class SettlementLevel {
    public static final String RULE = "SettlementLevel.v1";

    private static final int FIRST_STAGE = 1;

    private SettlementLevel() {}

    public static BoardLogEntry journalEntry(SettlementPlot plot, long tick, String configHash) {
        return journalEntry(plot, tick, configHash, List.of());
    }

    public static BoardLogEntry journalEntry(SettlementPlot plot, long tick, String configHash, List<String> extra) {
        Objects.requireNonNull(plot, "Участок не может быть null: без него нечего записывать в журнал");
        List<String> details = new ArrayList<>(List.of(
                "x=" + plot.anchor().x(),
                "y=" + plot.anchor().y(),
                "z=" + plot.anchor().z(),
                "min=" + plot.min(),
                "max=" + plot.max(),
                "columns=" + plot.columns(),
                "wood=" + plot.wood()));
        details.addAll(extra);
        return BoardLog.note(
                SettlementPlot.JOURNAL_ID, BoardCase.CLEAR_GROUNDS, tick, List.copyOf(details), null, configHash);
    }

    public static int of(SettlementPlot plot) {
        Objects.requireNonNull(plot, "Участок не может быть null: без него у поселения нет высоты");
        return plot.anchor().y();
    }

    public static boolean marked(String rule) {
        return RULE.equals(rule);
    }

    public static Optional<StageSite> site(TownPlan plan, int stage, int level) {
        Objects.requireNonNull(plan, "План не может быть null: места этапа берутся из него");
        return StageSites.find(plan, stage, level, TownRules.current(), orchardScheme());
    }

    public static Optional<StageSite> site(TownPlan plan, int stage, SettlementPlot plot) {
        Objects.requireNonNull(plot, "Участок не может быть null: порода сада берётся из него");
        return StageSites.find(plan, stage, of(plot), TownRules.current(), orchardScheme(), plot.wood());
    }

    private static String orchardScheme() {
        return StageRules.current()
                .scheme(BoardCase.BUILD_ORCHARD, 1)
                .orElseThrow(() -> new IllegalArgumentException(
                        "stages.json: нет схемы BUILD_ORCHARD уровня 1 — саду не из чего взять места деревьев"));
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

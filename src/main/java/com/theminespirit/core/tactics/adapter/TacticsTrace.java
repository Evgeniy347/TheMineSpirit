package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsMemory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TacticsTrace {
    public static final List<String> LABELS = List.of(
            "решение",
            "действие",
            "отход",
            "позвать на помощь",
            "состояние",
            "режим",
            "вернуться к",
            "обоснование",
            "память");

    private TacticsTrace() {}

    public static String format(TacticalOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("TacticsTrace.format: outcome = null; форматировать нечего");
        }
        return LABELS.get(0) + "=" + outcome.decision() + "; " + LABELS.get(1) + "=" + outcome.action() + "; "
                + LABELS.get(2) + "=" + outcome.retreatMethod() + "; " + LABELS.get(3) + "=" + outcome.callHelp() + "; "
                + LABELS.get(4) + "=" + outcome.state() + "; " + LABELS.get(5) + "=" + outcome.mode() + "; "
                + LABELS.get(6) + "=" + point(outcome.returnTo()) + "; " + LABELS.get(7) + "=["
                + outcome.trace().explain() + "]; " + LABELS.get(8) + "=[" + memory(outcome) + "]";
    }

    private static String memory(TacticalOutcome outcome) {
        TacticsMemory memory = outcome.memory();
        return "прежнее решение " + memory.previous().map(Enum::name).orElse("нет") + " принято "
                + memory.previousSource() + " на тике " + memory.decidedAt() + ", состояние с тика "
                + memory.stateSince() + ", якорь " + point(memory.anchor()) + ", враг виден на тике "
                + memory.lastSeenTick();
    }

    private static String point(Optional<Point> point) {
        return point.map(p -> String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", p.x(), p.y(), p.z()))
                .orElse("нет");
    }
}

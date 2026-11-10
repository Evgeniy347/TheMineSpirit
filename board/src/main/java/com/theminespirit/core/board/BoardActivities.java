package com.theminespirit.core.board;

import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BoardActivities {
    public static final String TARGET_KEY = "target=";
    public static final Set<BoardCase> CASES = EnumSet.of(
            BoardCase.ACTIVITY_ARM,
            BoardCase.ACTIVITY_BUILD,
            BoardCase.ACTIVITY_CHOP,
            BoardCase.ACTIVITY_COMBAT,
            BoardCase.ACTIVITY_CRAFT,
            BoardCase.ACTIVITY_DEFEND,
            BoardCase.ACTIVITY_ESCAPE,
            BoardCase.ACTIVITY_EXPEDITION,
            BoardCase.ACTIVITY_EXTERNAL,
            BoardCase.ACTIVITY_FARM,
            BoardCase.ACTIVITY_FORAGE,
            BoardCase.ACTIVITY_GUARD,
            BoardCase.ACTIVITY_MINE,
            BoardCase.ACTIVITY_MOVE,
            BoardCase.ACTIVITY_REST,
            BoardCase.ACTIVITY_RETREAT,
            BoardCase.ACTIVITY_RETURN,
            BoardCase.ACTIVITY_SENSE,
            BoardCase.ACTIVITY_SMELT,
            BoardCase.ACTIVITY_SOCIAL,
            BoardCase.ACTIVITY_UNDO,
            BoardCase.ACTIVITY_UNLOAD);
    private static final String GOTO = "goto";

    private BoardActivities() {}

    public static boolean isActivity(BoardCase boardCase) {
        return CASES.contains(boardCase);
    }

    public static boolean stale(BoardTicket ticket, @Nullable BoardStatus status, Set<String> liveCauses) {
        return status == BoardStatus.DONE
                && isActivity(ticket.boardCase())
                && ticket.boardCase() != BoardCase.ACTIVITY_EXTERNAL
                && !SupplyGate.retains(ticket, liveCauses);
    }

    public static Optional<BoardCase> caseOfScript(@Nullable String script) {
        if (script == null || script.isBlank()) {
            return Optional.empty();
        }
        return BoardScriptCards.of(script);
    }

    public static @Nullable String scriptOf(TaskNode plan) {
        Objects.requireNonNull(plan, "План задачи не может быть null: название берётся из него");
        if (plan instanceof ScriptCall call) {
            return call.script();
        }
        if (plan instanceof SequenceNode sequence) {
            String first = null;
            for (TaskNode step : sequence.steps()) {
                String name = scriptOf(step);
                if (name != null && !GOTO.equals(name)) {
                    return name;
                }
                first = first == null ? name : first;
            }
            return first;
        }
        if (plan instanceof RepeatNode repeat) {
            return scriptOf(repeat.step());
        }
        return null;
    }

    public static @Nullable String firstScript(TaskNode plan) {
        Objects.requireNonNull(plan, "План задачи не может быть null: первый шаг берётся из него");
        if (plan instanceof ScriptCall call) {
            return call.script();
        }
        if (plan instanceof SequenceNode sequence) {
            for (TaskNode step : sequence.steps()) {
                String first = firstScript(step);
                if (first != null) {
                    return first;
                }
            }
            return null;
        }
        if (plan instanceof RepeatNode repeat) {
            return firstScript(repeat.step());
        }
        return null;
    }

    public static @Nullable String cardScript(@Nullable String running, @Nullable TaskNode plan) {
        @Nullable String planned = plan == null ? null : scriptOf(plan);
        return planned != null ? planned : running;
    }

    public static @Nullable String targetOf(TaskNode plan) {
        Objects.requireNonNull(plan, "План задачи не может быть null: цель карточки берётся из него");
        if (plan instanceof ScriptCall call) {
            return targetOf(call.params());
        }
        if (plan instanceof SequenceNode sequence) {
            for (TaskNode step : sequence.steps()) {
                String target = targetOf(step);
                if (target != null) {
                    return target;
                }
            }
            return null;
        }
        if (plan instanceof RepeatNode repeat) {
            return targetOf(repeat.step());
        }
        return null;
    }

    private static @Nullable String targetOf(Map<String, Object> params) {
        Object x = params.get("x");
        Object y = params.get("y");
        Object z = params.get("z");
        if (x != null && y != null && z != null) {
            return x + "," + y + "," + z;
        }
        for (String key : List.of("target", "block", "item", "container", "species", "crop")) {
            Object value = params.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }
}

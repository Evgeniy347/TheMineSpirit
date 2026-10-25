package com.theminespirit.core.board;

import com.theminespirit.core.intellect.ActivityId;
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
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class BoardActivities {
    public static final String ID_PREFIX = "activity:";
    public static final String SCRIPT_KEY = "script=";
    public static final Set<BoardCase> CASES = EnumSet.of(
            BoardCase.ACTIVITY_ARM,
            BoardCase.ACTIVITY_CHOP,
            BoardCase.ACTIVITY_COMBAT,
            BoardCase.ACTIVITY_CRAFT,
            BoardCase.ACTIVITY_DEFEND,
            BoardCase.ACTIVITY_ESCAPE,
            BoardCase.ACTIVITY_EXPEDITION,
            BoardCase.ACTIVITY_EXTERNAL,
            BoardCase.ACTIVITY_FARM,
            BoardCase.ACTIVITY_FORAGE,
            BoardCase.ACTIVITY_MINE,
            BoardCase.ACTIVITY_REST,
            BoardCase.ACTIVITY_SMELT,
            BoardCase.ACTIVITY_UNLOAD);
    private static final String GOTO = "goto";

    private BoardActivities() {}

    public static boolean isActivity(BoardCase boardCase) {
        return CASES.contains(boardCase);
    }

    public static String idOf(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: у каждого духа одно задание-занятие");
        return ID_PREFIX + spirit;
    }

    public static BoardCase caseOf(ActivityId activity) {
        Objects.requireNonNull(activity, "Занятие интеллекта не может быть null: доска показывает его своим кейсом");
        return switch (activity) {
            case CHOP -> BoardCase.ACTIVITY_CHOP;
            case FARM -> BoardCase.ACTIVITY_FARM;
            case FORAGE -> BoardCase.ACTIVITY_FORAGE;
            case MINE -> BoardCase.ACTIVITY_MINE;
            case REST -> BoardCase.ACTIVITY_REST;
            case DEFEND -> BoardCase.ACTIVITY_DEFEND;
            case UNLOAD -> BoardCase.ACTIVITY_UNLOAD;
            case SMELT -> BoardCase.ACTIVITY_SMELT;
            case CRAFT -> BoardCase.ACTIVITY_CRAFT;
        };
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

    public static List<String> details(@Nullable String script) {
        return script == null || script.isBlank() ? List.of() : List.of(SCRIPT_KEY + script);
    }

    static BoardTicket ticket(UUID spirit, BoardCase boardCase, long now, List<String> details) {
        if (!isActivity(boardCase)) {
            throw new IllegalArgumentException("Кейс " + boardCase + " — не занятие: задание-занятие строится только "
                    + "из ACTIVITY_*, иначе доска спутает его с очередью");
        }
        return new BoardTicket(
                idOf(spirit),
                boardCase,
                Optional.of(spirit),
                BoardRules.priorityOf(boardCase),
                Math.max(0, now),
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of(),
                details);
    }
}

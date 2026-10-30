package com.theminespirit.core.board;

import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.tactics.TacticsAction;
import java.util.ArrayList;
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

    public static Optional<BoardCase> caseOf(TacticsAction action) {
        Objects.requireNonNull(action, "Действие тактики не может быть null: по нему подписывают занятие духа");
        return switch (action) {
            case FIGHT -> Optional.of(BoardCase.ACTIVITY_COMBAT);
            case RETREAT -> Optional.of(BoardCase.ACTIVITY_RETREAT);
            case RETURN -> Optional.of(BoardCase.ACTIVITY_RETURN);
            case GUARD -> Optional.of(BoardCase.ACTIVITY_GUARD);
            case IDLE -> Optional.empty();
        };
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

    public static Optional<BoardCase> caseOfScript(@Nullable String script) {
        if (script == null || script.isBlank()) {
            return Optional.empty();
        }
        return BoardScriptCards.of(script);
    }

    public record CardFacts(
            boolean removed,
            boolean running,
            boolean tacticsControl,
            @Nullable TacticsAction tactic,
            boolean ownActivity,
            @Nullable BoardCase launchedCase,
            boolean assigned,
            boolean defenceAssigned,
            @Nullable String script) {

        public Optional<BoardCase> card() {
            if (removed) {
                return Optional.empty();
            }
            if (running && "dig_out".equals(script)) {
                return Optional.of(BoardCase.ACTIVITY_ESCAPE);
            }
            if (ownActivity && launchedCase != null) {
                return Optional.of(launchedCase);
            }
            if (tacticsControl && !assigned && tactic != null) {
                Optional<BoardCase> card = caseOf(tactic);
                if (card.isPresent()) {
                    return card;
                }
            }
            if (running) {
                return Optional.of(caseOfScript(script).orElse(BoardCase.ACTIVITY_EXTERNAL));
            }
            if (assigned) {
                return Optional.empty();
            }
            if (defenceAssigned) {
                return Optional.of(BoardCase.ACTIVITY_DEFEND);
            }
            return Optional.empty();
        }
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

    public static List<String> details(@Nullable String script) {
        return details(script, null);
    }

    public static List<String> details(@Nullable String script, @Nullable String target) {
        if (script == null || script.isBlank()) {
            return List.of();
        }
        List<String> details = new ArrayList<>();
        details.add(SCRIPT_KEY + script);
        if (target != null && !target.isBlank()) {
            details.add(TARGET_KEY + target);
        }
        return List.copyOf(details);
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

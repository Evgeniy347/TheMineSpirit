package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class RepairPlan {
    public static final String PREFIX = "repair:";

    private RepairPlan() {}

    public static BoardTicket ticket(BoardCase step, Map<String, Integer> need, long now) {
        Objects.requireNonNull(step, "Шаг ремонта не может быть null: по нему выбирается схема восстановления");
        Objects.requireNonNull(need, "Нехватка ремонта не может быть null: пустую задают пустой картой");
        List<String> modifiers = step == BoardCase.BUILD_MINE
                ? List.of(ToolGate.TOOL_MODIFIER + ToolGate.PICKAXE, ToolGate.CARRY_MODIFIER + "cobblestone")
                : List.of();
        return new BoardTicket(
                PREFIX + step.name().toLowerCase(Locale.ROOT),
                BoardCase.REPAIR_BUILDING,
                Optional.empty(),
                BoardRules.repairPriorityOf(step),
                now,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.copyOf(need),
                modifiers);
    }

    public static Optional<TicketTarget> target(TaskNode plan) {
        Objects.requireNonNull(plan, "План постройки не может быть null: схема восстановления берётся из него");
        List<ScriptCall> found = new ArrayList<>();
        collect(plan, found);
        if (found.size() != 1) {
            return Optional.empty();
        }
        ScriptCall call = found.get(0);
        if (!(call.params().get("file") instanceof String file)
                || !(call.params().get("x") instanceof Integer x)
                || !(call.params().get("y") instanceof Integer y)
                || !(call.params().get("z") instanceof Integer z)) {
            return Optional.empty();
        }
        TicketTarget target = TicketTarget.at(new BlockCoord(x, y, z)).withScheme(file);
        if (call.params().get("wood") instanceof String wood) {
            target = target.withWood(wood);
        }
        return Optional.of(target);
    }

    public static boolean goalless(BoardTicket ticket, @Nullable TicketTarget target) {
        Objects.requireNonNull(ticket, "Задание не может быть null: проверяется цель ремонта");
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING) {
            return false;
        }
        return target == null || (target.work() == null && target.schemeFile() == null && target.leveling() == null);
    }

    private static void collect(TaskNode plan, List<ScriptCall> found) {
        if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
        } else if (plan instanceof ScriptCall call
                && "build_schematic".equals(call.script())
                && !call.params().containsKey("rotation")) {
            found.add(call);
        }
    }
}

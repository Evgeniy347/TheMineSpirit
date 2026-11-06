package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LevelRepairTests {
    private static final BlockCoord ANCHOR = new BlockCoord(0, 64, 0);

    @Test
    void plan_HoleAndExtraBlock_CutThenOneDirtBatchThenFillBottomUp() {
        PlotLeveling leveling = LevelRepair.leveling(
                List.of(new BlockCoord(2, 63, 2), new BlockCoord(2, 62, 2)), List.of(new BlockCoord(-3, 64, 1)));
        TaskNode plan = TicketPlans.plan(
                BoardCase.REPAIR_BUILDING, 1, TicketTarget.at(ANCHOR).withLeveling(leveling));
        List<ScriptCall> calls = new ArrayList<>();
        collect(plan, calls);
        assertEquals(
                List.of("clear_area", "collect_drops", "acquire_at", "fill", "fill"),
                calls.stream()
                        .map(ScriptCall::script)
                        .filter(script -> !"wait".equals(script))
                        .toList(),
                "Восстановление выравнивания: лишний блок срезается, земля на все дыры — одной партией, дыры "
                        + "засыпаются; иначе аудит ровности не возвращает участок к плану");
        assertEquals(
                List.of(62, 63),
                calls.stream()
                        .filter(call -> "fill".equals(call.script()))
                        .map(call -> call.params().get("y1"))
                        .toList(),
                "Дыра засыпается снизу вверх: верхняя клетка без опоры под ней не встаёт");
    }

    private static void collect(TaskNode node, List<ScriptCall> out) {
        switch (node) {
            case ScriptCall call -> out.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, out));
            case FallbackNode fallback -> collect(fallback.steps().get(0), out);
            default -> {}
        }
    }
}

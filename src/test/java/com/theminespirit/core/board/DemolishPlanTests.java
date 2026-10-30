package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ClearAreaParams;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class DemolishPlanTests {
    @Test
    void plan_DemolishUnreachableTop_ClearsBottomUpWithoutGoto() {
        StageSite site = TownFixtures.flatSite();
        BlockCoord top = new BlockCoord(-169, 80, -514);
        BlockCoord bottom = new BlockCoord(-170, 78, -514);
        StageWork work = new StageWork(
                site,
                List.of(new PlannedBlock(top, "minecraft:air"), new PlannedBlock(bottom, "minecraft:air")),
                List.of());
        TaskNode plan = TicketPlans.plan(
                BoardCase.DISMANTLE_SHELTER, TicketTarget.at(top).withWork(work));

        List<Integer> heights = calls(plan, "clear_area").stream()
                .map(call -> ClearAreaParams.from(call.params()).y1())
                .toList();

        assertEquals(
                List.of(78, 80),
                heights,
                "Разбор идёт снизу вверх и не ведёт духа к верхней висящей клетке: прежний переход к y=80 "
                        + "застревал в петле переносов и проваливал задание каждую минуту");
        assertEquals(
                List.of(),
                calls(plan, "goto"),
                "В плане разбора нет отдельного перехода к блоку: иначе недостижимая цель снова и снова "
                        + "получает «застрял по пути», а clear_area даже не начинается");
    }

    private static List<ScriptCall> calls(TaskNode root, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> {
                    if (call.script().equals(script)) {
                        calls.add(call);
                    }
                }
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                default -> {}
            }
        }
        return calls;
    }
}

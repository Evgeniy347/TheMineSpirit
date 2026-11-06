package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehousePlanTests {
    private static final BlockCoord AT = new BlockCoord(10, 64, 10);

    @Test
    void plan_BuildWarehouse_EachLevelUsesItsOwnScheme() {
        for (int level = 1; level <= WarehouseRules.MAX_LEVEL; level++) {
            TaskNode plan = TicketPlans.plan(BoardCase.BUILD_WAREHOUSE, level, TicketTarget.at(AT));
            ScriptCall build = find(plan, "build_schematic");
            assertEquals(
                    WarehouseRules.scheme(level),
                    build.params().get("file"),
                    "Уровень " + level + " склада строится своей схемой: чужая схема дала бы не тот "
                            + "размер и не то число сундуков");
        }
    }

    @Test
    void plan_RelocateWarehouse_CollectsDropsAndUnloadsIntoNewChests() {
        BlockCoord from = new BlockCoord(10, 64, 10);
        BlockCoord to = new BlockCoord(42, 64, 10);
        TaskNode plan = TicketPlans.plan(
                BoardCase.RELOCATE_BUILDING,
                TicketTarget.at(to).withRelocate(from, to).withScheme("storage_v2"));

        List<String> scripts = scripts(plan);
        assertTrue(
                scripts.contains("clear_area"),
                "Перенос склада обязан снести старый склад: иначе две постройки остались бы стоять");
        assertTrue(
                scripts.contains("collect_drops"),
                "Перенос склада обязан подобрать выпавшие из старых сундуков вещи: иначе они остались бы на земле");
        assertTrue(
                scripts.contains("unload"),
                "Перенос склада обязан разложить вещи в сундуки нового склада: иначе груз остался бы в инвентаре");
        assertTrue(
                !scripts.contains("build_schematic"),
                "Новый склад уже построен: перенос не должен строить его второй раз");
    }

    private static ScriptCall find(TaskNode node, String script) {
        List<ScriptCall> calls = calls(node);
        return calls.stream()
                .filter(call -> script.equals(call.script()))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> scripts(TaskNode node) {
        List<String> names = new ArrayList<>();
        for (ScriptCall call : calls(node)) {
            names.add(call.script());
        }
        return List.copyOf(names);
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return List.copyOf(calls);
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
        }
    }
}

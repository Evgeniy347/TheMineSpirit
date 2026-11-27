package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldSupplyPlanTests {
    @Test
    void placed_FieldPlan_RequiresTorchesAndWaterBuckets() {
        TicketTarget target = TicketTarget.at(new BlockCoord(-134, 63, -467)).withScheme("wheat_field_1");
        TaskNode plan = StagePlans.plan(BoardCase.BUILD_PLACED, 1, target).orElseThrow();
        List<ScriptCall> calls = new ArrayList<>();
        collect(plan, calls);

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:torch".equals(call.params().get("item"))),
                "Поле обязательно получает факелы до стройки: иначе семена выпадают ночью. При смене "
                        + "placed_FieldPlan_RequiresTorchesAndWaterBuckets проверь StagePlans.placed.");
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:water_bucket".equals(call.params().get("item"))),
                "План поля требует заполненные вёдра: иначе потерянный источник не попадает в потребность "
                        + "графа и ремонт воды нечем выполнить. При смене placed_FieldPlan_RequiresTorchesAndWaterBuckets "
                        + "проверь StagePlans.placed.");
        assertFalse(
                calls.stream().anyMatch(call -> "wait".equals(call.script())),
                "Отказ получения факела не проглатывается ожиданием: иначе стройка начинается без света. "
                        + "При смене placed_FieldPlan_RequiresTorchesAndWaterBuckets проверь acquireStrict.");
        assertFalse(
                calls.stream().anyMatch(call -> "goto".equals(call.script())),
                "Пашня не делает лишний goto к входу: иначе готовая схема не успевает провериться и "
                        + "дух снова застревает в рельефе. При смене placed_FieldPlan_RequiresTorchesAndWaterBuckets "
                        + "проверь StagePlans.placed.");
        assertFalse(
                calls.stream().anyMatch(call -> "clear_area".equals(call.script())),
                "Пашня не запускает общую расчистку перед схемой: иначе готовые земля, вода и посевы "
                        + "сносятся повторно. При смене placed_FieldPlan_RequiresTorchesAndWaterBuckets "
                        + "проверь StagePlans.placed.");
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
        } else if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}

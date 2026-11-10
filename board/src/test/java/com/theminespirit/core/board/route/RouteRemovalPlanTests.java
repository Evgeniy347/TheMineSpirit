package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteRemovalPlanTests {
    private static final String CLEAR_AREA = "clear_area";

    @Test
    void removal_ClearGroundsPlan_ClearsTrailWithoutBuildingItBack() {
        RouteTrail trail = new RouteTrail(List.of(new BlockCoord(3, 64, 3), new BlockCoord(4, 64, 3)), "sand");
        TaskNode plan = TicketPlans.plan(
                BoardCase.CLEAR_GROUNDS, TicketTarget.at(trail.first()).withLeveling(trail.removal()));

        List<ScriptCall> calls = calls(plan);
        List<ScriptCall> clears =
                calls.stream().filter(call -> CLEAR_AREA.equals(call.script())).toList();
        List<ScriptCall> fills =
                calls.stream().filter(call -> "fill".equals(call.script())).toList();

        assertFalse(clears.isEmpty(), "Снятие старой тропы чистит её клетки скриптом clear_area: " + calls);
        assertEquals(3, clears.get(0).params().get("x1"), "Срезка начинается с первой клетки старой тропы");
        assertEquals(4, clears.get(0).params().get("x2"), "Срезка доходит до последней клетки старой тропы");
        assertEquals(64, clears.get(0).params().get("y1"), "Срезка идёт по высоте проложенной тропы");
        assertTrue(fills.isEmpty(), "Снятие ничего не строит заново: старую тропу убирают, а не перекладывают");
    }

    @Test
    void removal_PathMaterial_IsCollectedAndDeposited() {
        RouteTrail trail = new RouteTrail(List.of(new BlockCoord(3, 64, 3)), "sand");
        TaskNode plan = TicketPlans.plan(
                BoardCase.CLEAR_GROUNDS, TicketTarget.at(trail.first()).withLeveling(trail.removal()));

        List<ScriptCall> calls = calls(plan);
        ScriptCall collect = calls.stream()
                .filter(call -> "collect_drops".equals(call.script()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Снятие тропы обязано собрать выпавшие блоки: " + calls));
        Object filter = collect.params().get("filter");
        assertTrue(
                String.valueOf(filter).contains("minecraft:sand"),
                "Песчаная тропа собирает песок, а не только землю: иначе след остаётся предметами на земле");
        assertTrue(
                calls.stream()
                        .filter(call -> "deposit".equals(call.script()))
                        .anyMatch(call -> "minecraft:sand".equals(call.params().get("item"))),
                "Собранный материал тропы уходит в сундук, а не висит в руках духа: " + calls);
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        collect(node, found);
        return List.copyOf(found);
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        }
    }
}

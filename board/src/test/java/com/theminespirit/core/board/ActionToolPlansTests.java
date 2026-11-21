package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ActionToolPlansTests {
    @Test
    void shovel_TakesReadyToolFromChests_WarehouseSecond() {
        assertToolSteps(ActionToolPlans.shovel(), "*_shovel");
    }

    @Test
    void hoe_TakesReadyToolFromChests_WarehouseSecond() {
        assertToolSteps(ActionToolPlans.hoe(), "*_hoe");
    }

    private static void assertToolSteps(TaskNode plan, String kind) {
        FallbackNode fallback = (FallbackNode) plan;
        List<ScriptCall> calls =
                fallback.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("withdraw", "withdraw"),
                calls.stream().map(ScriptCall::script).toList(),
                "Инструмент только берут из сундуков: крафт ставит граф отдельным узлом, а не местный acquire"
                        + " Иначе тест assertToolSteps перестанет ловить эту поломку. При смене assertToolSteps проверь это утверждение.");
        assertEquals(
                "auto",
                calls.get(0).params().get("container"),
                "Сначала ближний сундук"
                        + " Иначе тест assertToolSteps перестанет ловить эту поломку. При смене assertToolSteps проверь это утверждение.");
        assertEquals(
                "warehouse",
                calls.get(1).params().get("container"),
                "Затем склад поселения"
                        + " Иначе тест assertToolSteps перестанет ловить эту поломку. При смене assertToolSteps проверь это утверждение.");
        assertTrue(
                calls.stream().allMatch(call -> kind.equals(call.params().get("item"))),
                "Ищут инструмент нужного вида целиком, а не выбранный заранее материал: " + calls
                        + " Иначе тест assertToolSteps перестанет ловить эту поломку. При смене assertToolSteps проверь это утверждение.");
        assertTrue(
                calls.stream()
                        .allMatch(
                                call -> Integer.valueOf(1).equals(call.params().get("count"))),
                "Нужен один инструмент"
                        + " Иначе тест assertToolSteps перестанет ловить эту поломку. При смене assertToolSteps проверь это утверждение.");
    }
}

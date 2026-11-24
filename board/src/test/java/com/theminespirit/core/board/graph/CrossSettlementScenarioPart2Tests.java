package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossSettlementScenarioPart2Tests {
    private static int index(List<ScriptCall> calls, String script) {
        for (int position = 0; position < calls.size(); position++) {
            if (script.equals(calls.get(position).script())) {
                return position;
            }
        }
        return -1;
    }

    private static List<String> scriptsOf(List<ScriptCall> calls) {
        return calls.stream().map(ScriptCall::script).toList();
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }

    @Test
    void demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking() {
        GraphWorld world =
                stageOne().event("demolish_chest").event("drops_nearby").event("inventory_overloaded");
        GraphFacts facts = new GraphFacts(
                named(spirit("spirit-1")),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(new GraphSiteFacts.Stray(
                        "stray:chest@1,64,1",
                        "chest",
                        new GraphFacts.Position(1, 64, 1),
                        "spirit-1",
                        Map.of("minecraft:chest", 1))),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task demolition = task(plan, "event:demolish_stray:demolish_stray");
        assertTrue(
                demolition.feasible(),
                "Лишний сундук получает задание сноса:  Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение."
                        + ids(plan));
        GraphPlan.Task salvage = task(plan, "event:demolish_chest:demolish_chest");
        assertTrue(
                salvage.feasible(),
                "Разбор сундука с подбором выполним через штатное событие графа Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение.");
        assertTrue(
                salvage.priority()
                        > task(plan, "event:drops_nearby:collect_drops").priority(),
                "Содержимое разбираемого сундука важнее обычного фонового подбора Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение.");
        assertTrue(
                salvage.priority()
                        < task(plan, "event:inventory_overloaded:unload_overloaded")
                                .priority(),
                "Высокий приоритет разбора не должен мешать необходимой разгрузке Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение.");
        List<Stack> slots = new ArrayList<>(List.of(new Stack("minecraft:bread", 0, 32, 64)));
        ContainerSnapshot chest = new ContainerSnapshot(new BlockCoord(1, 64, 1), slots);
        List<ScriptCall> calls = new ArrayList<>();
        collect(ChestPlans.demolish(chest, List.of(new BlockCoord(1, 64, 1))), calls);
        int withdraw = index(calls, "withdraw");
        int deposit = index(calls, "deposit");
        int clear = index(calls, "clear_area");
        assertTrue(
                withdraw >= 0,
                "Сундук сначала вынимают скриптом withdraw:  Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение."
                        + scriptsOf(calls));
        assertTrue(
                deposit > withdraw,
                "Вынутое относят на склад до сноса:  Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение."
                        + scriptsOf(calls));
        assertTrue(
                clear > deposit,
                "Ломают сундук только после выноса:  Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение."
                        + scriptsOf(calls));
        assertEquals(
                "minecraft:bread",
                calls.get(withdraw).params().get("item"),
                "Из сундука вынимают именно его содержимое, а не первый попавшийся предмет:  Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение."
                        + calls);
        assertTrue(
                BoardActivities.caseOfScript("clear_area")
                        .filter(BoardCase.ACTIVITY_BUILD::equals)
                        .isPresent(),
                "Снос идёт действием разбора, а не новой механикой Иначе тест demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking перестанет ловить эту поломку. При смене demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking проверь это утверждение.");
    }
}

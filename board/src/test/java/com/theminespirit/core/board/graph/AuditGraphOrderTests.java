package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.Access;
import com.theminespirit.core.board.audit.DiffItem;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.SourceRef;
import com.theminespirit.core.board.audit.Wanted;
import com.theminespirit.core.inventory.api.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class AuditGraphOrderTests {
    private static final Path DATA = Path.of("mod/src/main/resources/data/theminespirit/board/graph.d/audit.json");

    private static GraphEvent event() throws IOException {
        return GraphEvent.parse(JsonResource.parse(Files.readString(DATA), DATA.toString()))
                .get("audit");
    }

    private static GraphEvent.Task task(GraphEvent event, String id) {
        GraphEvent.Task found = event.tasks().stream()
                .filter(task -> id.equals(task.id()))
                .findFirst()
                .orElse(null);
        return Objects.requireNonNull(
                found, "Задача " + id + " обязана быть в шаблоне audit: без неё граф не найдёт группу");
    }

    @Test
    void auditOrder_SettlementLadder_RunsRoadsBeforeBuildings() throws IOException {
        GraphEvent event = event();
        List<String> order = List.of(
                "street:s-1",
                "building:storage",
                "street:s-2",
                "building:orchard",
                "street:s-3",
                "building:field_wheat",
                "wall",
                "lighting");

        for (int index = 1; index < order.size(); index++) {
            GraphEvent.Task previous = task(event, order.get(index - 1));
            GraphEvent.Task next = task(event, order.get(index));
            assertTrue(
                    previous.base() > next.base(),
                    "Порядок " + previous.id() + " → " + next.id() + " нарушен: иначе граф начнёт сад или"
                            + " стену раньше дороги к складу. Это проверка последовательности, а не конкретных"
                            + " чисел: при смене лестницы перепроверь audit.json и GraphEvent.find.");
        }
    }

    @Test
    void auditOrder_GroundHazards_PrecedeDecoration() throws IOException {
        GraphEvent event = event();

        assertTrue(
                task(event, "ground:pit").base() > task(event, "ground:stray").base(),
                "Яма под ногами важнее декоративной расчистки: иначе дух сначала уберёт цветок и не выйдет"
                        + " из ямы. При смене весов перепроверь audit.json.");
        assertTrue(
                task(event, "ground:stray").base() > task(event, "ground").base(),
                "Лишний блок важнее общего дальнего грунта: иначе дорога к складу останется закрытой."
                        + " При смене весов перепроверь audit.json.");
    }

    @Test
    void auditGraph_Planner_EmitsGroupNodeAndMaterials() throws IOException {
        SupplyGraph base = GraphWorld.GRAPH;
        Map<String, GraphEvent> events = new LinkedHashMap<>(base.events());
        events.put("audit", event());
        SupplyGraph graph = new SupplyGraph(
                base.decay(),
                base.urgentBoost(),
                base.buildReadyBoost(),
                base.maxPriority(),
                base.tiers(),
                base.kit(),
                base.storage(),
                base.items(),
                base.buildings(),
                base.ways(),
                base.edges(),
                base.unimplemented(),
                events,
                base.groups(),
                base.triggers(),
                base.herdCaps(),
                base.buildStartShare(),
                base.costs());
        BlockCoord cell = new BlockCoord(0, 64, 0);
        Group group = Group.pending(
                Group.Kind.STREET,
                "s-7",
                new ChunkCell(0, 0),
                List.of(DiffItem.place(
                        cell,
                        "minecraft:air",
                        Wanted.block("minecraft:stone"),
                        SourceRef.street("s-7"),
                        Access.SPIRIT)),
                1);
        GraphWorld world = GraphWorld.empty();
        world.graph = graph;
        world.facts(GraphFacts.empty().withAuditGroups(List.of(group)));

        GraphPlan missing = GraphPlanner.plan(graph, world.state());
        GraphPlan.Task blocked = missing.tasks().stream()
                .filter(task -> "event:audit:street:s-7@0,0".equals(task.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                Map.of("stone", 1),
                blocked.consumes(),
                "Узел группы несёт материалы из DiffItem: иначе"
                        + " граф не закажет камень, а дух получит невыполнимую работу. При смене материалов перепроверь"
                        + " AuditGraphFacts.materials.");
        assertTrue(
                !blocked.feasible(),
                "Без камня группа остаётся в графе, но невыполнимой: иначе дух"
                        + " пойдёт ломать руки вместо ожидания материала. При смене гейта перепроверь GraphTaskBuilder.auditTask.");

        world.with("stone", 1);
        GraphPlan ready = GraphPlanner.plan(graph, world.state());
        assertTrue(
                ready.tasks().stream()
                        .filter(task -> "event:audit:street:s-7@0,0".equals(task.id()))
                        .anyMatch(GraphPlan.Task::feasible),
                "С камнем тот же адресный узел становится выполнимым: иначе граф не выдаст группу духу."
                        + " При смене гейта перепроверь GraphTaskBuilder.auditTask.");
    }
}

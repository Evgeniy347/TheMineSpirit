package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphCraftingTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void handles_BreadWay_UsesCraftingInsteadOfGather() {
        GraphPlan.Task task = task("craft_bread", "bread");
        GraphTickets.Ticket ticket = ticket("craft_bread", task);

        assertTrue(
                GraphCrafting.handles(ticket, task),
                "Путь craft_bread обязан идти в крафт, а не в gather: иначе дух ищет несуществующий предмет "
                        + "bread в мире и хлеб никогда не появляется. При смене GraphCrafting.handles проверь "
                        + "GraphPost.way, TicketStoragePlans.gatherMaterials и живые события craft_bread.");
        assertEquals(
                "minecraft:bread",
                GraphCrafting.item(GRAPH, task, "oak"),
                "Крафт хлеба переводит узел bread в предмет Minecraft: иначе CraftScript получит абстрактный "
                        + "food и откажет без рецепта. При смене GraphCrafting.item проверь craft_bread и craft_food.");
    }

    @Test
    void item_LegacyFoodWay_MapsToBread() {
        assertEquals(
                "minecraft:bread",
                GraphCrafting.item(GRAPH, task("craft_food", "food"), "oak"),
                "Старый путь craft_food делает хлеб, а не абстрактный предмет food: иначе граф снова выдаст "
                        + "невозможную добычу пищи. При смене GraphCrafting.item проверь DemandFoodChainScenarioTests "
                        + "и StockCatalog.nutrition.");
    }

    @Test
    void handles_HarvestWay_DoesNotUseCrafting() {
        GraphPlan.Task task = task("harvest_crop", "wheat");
        GraphTickets.Ticket ticket = ticket("harvest_crop", task);

        assertFalse(
                GraphCrafting.handles(ticket, task),
                "Урожай собирается фермерским действием, а не крафтом: иначе пшеница потеряет сбор и "
                        + "продовольственная цепочка встанет. При смене GraphCrafting.handles проверь FarmPost.special.");
    }

    private static GraphPlan.Task task(String id, String makes) {
        return new GraphPlan.Task(
                id, BoardCase.GATHER_MATERIALS, makes, Optional.empty(), 1, Map.of("wheat", 3), true, false, true, 50);
    }

    private static GraphTickets.Ticket ticket(String node, GraphPlan.Task task) {
        return new GraphTickets.Ticket("graph:" + node, GraphTickets.Kind.WAY, node, task, 1);
    }
}

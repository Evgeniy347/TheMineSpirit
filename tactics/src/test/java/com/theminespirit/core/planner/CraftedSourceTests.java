package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.api.CraftedItems;
import com.theminespirit.core.planner.api.Ingredient;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import com.theminespirit.core.planner.api.UnplannableGoalException;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CraftedSourceTests {
    private static final String PLANKS = "oak_planks";
    private static final String SAPLING = "oak_sapling";
    private static final int STATION_RADIUS = CatalogLimits.STATION_RADIUS_DEFAULT;

    @Test
    void resolve_CraftedPlanksWithoutRecipe_RefusesWorldGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of(PLANKS), Map.of(), Map.of());

        UnplannableGoalException error = assertThrows(
                UnplannableGoalException.class,
                () -> CorePlanner.resolve(PLANKS, 148, knowledge),
                "Доски — крафтовый предмет: при потере рецепта резолвер обязан назвать причину, а не искать "
                        + "37 размещённых досок по постройкам (аномалия A, oak_planks ×148)"
                        + " Иначе тест resolve_CraftedPlanksWithoutRecipe_RefusesWorldGather перестанет ловить эту поломку. При смене resolve_CraftedPlanksWithoutRecipe_RefusesWorldGather проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(PLANKS) && error.getMessage().contains("крафтом"),
                "Причина обязана называть предмет и то, что он делается крафтом: по ней доска ставит честный "
                        + "BLOCKED, а не 5 провалов добычи. Текст: " + error.getMessage()
                        + " Иначе тест resolve_CraftedPlanksWithoutRecipe_RefusesWorldGather перестанет ловить эту поломку. При смене resolve_CraftedPlanksWithoutRecipe_RefusesWorldGather проверь это утверждение.");
    }

    @Test
    void resolve_CraftedPlanksWithRecipe_CraftsFromLogs() {
        ResourceKnowledge knowledge = new StubKnowledge(
                Set.of(PLANKS, "oak_log"),
                Map.of(PLANKS, List.of(new Ingredient("oak_log", 1))),
                Map.of(),
                Map.of(PLANKS, 4));

        TaskNode plan = CorePlanner.resolve(PLANKS, 8, knowledge);

        assertEquals(
                new SequenceNode(List.of(wood("oak_log", 2), craft(PLANKS, 8))),
                plan,
                "Живой рецепт обязан побеждать сбор размещённых досок: 2 бревна дают 8 досок крафтом"
                        + " Иначе тест resolve_CraftedPlanksWithRecipe_CraftsFromLogs перестанет ловить эту поломку. При смене resolve_CraftedPlanksWithRecipe_CraftsFromLogs проверь это утверждение.");
    }

    @Test
    void resolve_CraftedSaplingWithoutRecipe_RefusesWorldGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of(SAPLING), Map.of(), Map.of());

        UnplannableGoalException error = assertThrows(
                UnplannableGoalException.class,
                () -> CorePlanner.resolve(SAPLING, 4, knowledge),
                "Саженец не добывается выкапыванием посаженного: без рецепта источник — листва (GatherPlans), "
                        + "а не размещённые саженцы в мире (N8)"
                        + " Иначе тест resolve_CraftedSaplingWithoutRecipe_RefusesWorldGather перестанет ловить эту поломку. При смене resolve_CraftedSaplingWithoutRecipe_RefusesWorldGather проверь это утверждение.");

        assertTrue(
                error.getMessage().contains(SAPLING),
                "Причина обязана называть саженец: иначе в логе снова «не удалось добыть oak_sapling ×4» без "
                        + "источника. Текст: " + error.getMessage()
                        + " При смене resolve_CraftedSaplingWithoutRecipe_RefusesWorldGather проверь это утверждение.");
    }

    @Test
    void resolve_NamespacedPlanksWithoutRecipe_RefusesWorldGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("minecraft:oak_planks"), Map.of(), Map.of())
                .withGatherSource("minecraft:oak_planks", "oak_planks");

        assertThrows(
                UnplannableGoalException.class,
                () -> CorePlanner.resolve("minecraft:oak_planks", 67, knowledge),
                "Пространство имён не делает доски другим блоком: 'minecraft:oak_planks' и 'oak_planks' — "
                        + "один предмет, и добыча размещённых досок запрещена так же, как для короткого id (A)"
                        + " Иначе тест resolve_NamespacedPlanksWithoutRecipe_RefusesWorldGather перестанет ловить эту поломку. При смене resolve_NamespacedPlanksWithoutRecipe_RefusesWorldGather проверь это утверждение.");
    }

    @Test
    void resolve_DropSourcedCraftedFamily_GathersSourceBlock() {
        ResourceKnowledge knowledge =
                new StubKnowledge(Set.of("glass"), Map.of(), Map.of()).withGatherSource("glass", "sand");

        TaskNode plan = CorePlanner.resolve("glass", 3, knowledge);

        assertEquals(
                gather("sand", 3),
                plan,
                "Предмет с настоящим блоком-источником по-прежнему берётся из мира: правило крафтовых семейств "
                        + "не должно отменять добычу сырья"
                        + " Иначе тест resolve_DropSourcedCraftedFamily_GathersSourceBlock перестанет ловить эту поломку. При смене resolve_DropSourcedCraftedFamily_GathersSourceBlock проверь это утверждение.");
    }

    @Test
    void resolve_NaturalBlock_StillGathersFromWorld() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("dirt"), Map.of(), Map.of());

        TaskNode plan = CorePlanner.resolve("dirt", 5, knowledge);

        assertEquals(
                gather("dirt", 5),
                plan,
                "Природный блок остаётся добычей: правило не должно запрещать сбор земли, песка и камня"
                        + " Иначе тест resolve_NaturalBlock_StillGathersFromWorld перестанет ловить эту поломку. При смене resolve_NaturalBlock_StillGathersFromWorld проверь это утверждение.");
    }

    @Test
    void resolve_EveryCraftedFamilyWithoutRecipe_RefusesWorldGather() {
        for (String item : List.of(
                PLANKS,
                SAPLING,
                "oak_fence",
                "oak_door",
                "oak_stairs",
                "oak_slab",
                "torch",
                "chest",
                "glass",
                "bookshelf",
                "stone_bricks",
                "stone_brick_stairs")) {
            ResourceKnowledge knowledge = new StubKnowledge(Set.of(item), Map.of(), Map.of());

            assertThrows(
                    UnplannableGoalException.class,
                    () -> CorePlanner.resolve(item, 4, knowledge),
                    "Крафтовый '" + item + "' без рецепта обязан отказывать на любом пути выдачи (graph:make, "
                            + "supply, supply_gather, acquire), а не искать размещённую копию блоком в мире"
                            + " Иначе тест resolve_EveryCraftedFamilyWithoutRecipe_RefusesWorldGather перестанет ловить эту поломку. При смене resolve_EveryCraftedFamilyWithoutRecipe_RefusesWorldGather проверь это утверждение.");
        }
    }

    @Test
    void isCrafted_Families_True() {
        for (String item : List.of(
                PLANKS,
                SAPLING,
                "oak_fence",
                "oak_stairs",
                "oak_door",
                "torch",
                "chest",
                "stone_bricks",
                "wooden_axe",
                "stone_pickaxe")) {
            assertTrue(
                    CraftedItems.isCrafted(item),
                    "Предмет '" + item + "' делается крафтом: добыча его размещённых копий — та же ошибка, "
                            + "что с досками и саженцами"
                            + " Иначе тест isCrafted_Families_True перестанет ловить эту поломку. При смене isCrafted_Families_True проверь это утверждение.");
        }
    }

    @Test
    void resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock() {
        ResourceKnowledge knowledge =
                new StubKnowledge(Set.of("stone_bricks", "stone_brick_stairs"), Map.of(), Map.of());

        UnplannableGoalException bricks = assertThrows(
                UnplannableGoalException.class,
                () -> CorePlanner.resolve("stone_bricks", 111, knowledge),
                "Кирпичи — крафтовый предмет: без рецепта сбор обязан отказать, а не искать блок "
                        + "(-329,63,-515) в 150 блоках от поселения (observe18 B5)"
                        + " Иначе тест resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock перестанет ловить эту поломку. При смене resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock проверь это утверждение.");
        UnplannableGoalException stairs = assertThrows(
                UnplannableGoalException.class,
                () -> CorePlanner.resolve("stone_brick_stairs", 1, knowledge),
                "Ступени кирпича — крафтовый предмет: без рецепта дух не идёт за блоком (-18,74,-421)"
                        + " Иначе тест resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock перестанет ловить эту поломку. При смене resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock проверь это утверждение.");

        assertTrue(
                bricks.getMessage().contains("stone_bricks")
                        && bricks.getMessage().contains("крафтом"),
                "Отказ обязан называть кирпичи и крафт: " + bricks.getMessage()
                        + " Иначе тест resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock перестанет ловить эту поломку. При смене resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock проверь это утверждение.");
        assertTrue(
                stairs.getMessage().contains("stone_brick_stairs"),
                "Отказ обязан называть ступени: " + stairs.getMessage()
                        + " Иначе тест resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock перестанет ловить эту поломку. При смене resolve_StoneBricksWithoutRecipe_RefusesFarWorldBlock проверь это утверждение.");
    }

    @Test
    void isCrafted_NaturalItems_False() {
        for (String item : List.of("oak_log", "dirt", "stone", "coal_ore", "raw_iron", "oak_leaves")) {
            assertFalse(
                    CraftedItems.isCrafted(item),
                    "Природный '" + item + "' не крафтовый: запрет сбора сломал бы добычу сырья"
                            + " Иначе тест isCrafted_NaturalItems_False перестанет ловить эту поломку. При смене isCrafted_NaturalItems_False проверь это утверждение.");
        }
    }

    private static ScriptCall gather(String block, int count) {
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }

    private static TaskNode wood(String block, int count) {
        int trees = Math.min(CatalogLimits.COUNT_MAX, Math.max(1, (count + 3) / 4));
        ScriptCall chop = new ScriptCall(
                "chop_tree",
                Map.of("species", "oak", "count", trees, "radius", CatalogLimits.HARVEST_RADIUS_MAX, "replant", false));
        return new FallbackNode(List.of(chop, gather(block, count)));
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS, "place", false));
    }
}

package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
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
                () -> ResourceResolver.resolve(PLANKS, 148, knowledge),
                "Доски — крафтовый предмет: при потере рецепта резолвер обязан назвать причину, а не искать "
                        + "37 размещённых досок по постройкам (аномалия A, oak_planks ×148)");

        assertTrue(
                error.getMessage().contains(PLANKS) && error.getMessage().contains("крафтом"),
                "Причина обязана называть предмет и то, что он делается крафтом: по ней доска ставит честный "
                        + "BLOCKED, а не 5 провалов добычи. Текст: " + error.getMessage());
    }

    @Test
    void resolve_CraftedPlanksWithRecipe_CraftsFromLogs() {
        ResourceKnowledge knowledge = new StubKnowledge(
                Set.of(PLANKS, "oak_log"),
                Map.of(PLANKS, List.of(new Ingredient("oak_log", 1))),
                Map.of(),
                Map.of(PLANKS, 4));

        TaskNode plan = ResourceResolver.resolve(PLANKS, 8, knowledge);

        assertEquals(
                new SequenceNode(List.of(gather("oak_log", 2), craft(PLANKS, 8))),
                plan,
                "Живой рецепт обязан побеждать сбор размещённых досок: 2 бревна дают 8 досок крафтом");
    }

    @Test
    void resolve_CraftedSaplingWithoutRecipe_RefusesWorldGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of(SAPLING), Map.of(), Map.of());

        UnplannableGoalException error = assertThrows(
                UnplannableGoalException.class,
                () -> ResourceResolver.resolve(SAPLING, 4, knowledge),
                "Саженец не добывается выкапыванием посаженного: без рецепта источник — листва (GatherPlans), "
                        + "а не размещённые саженцы в мире (N8)");

        assertTrue(
                error.getMessage().contains(SAPLING),
                "Причина обязана называть саженец: иначе в логе снова «не удалось добыть oak_sapling ×4» без "
                        + "источника. Текст: " + error.getMessage());
    }

    @Test
    void resolve_NamespacedPlanksWithoutRecipe_RefusesWorldGather() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("minecraft:oak_planks"), Map.of(), Map.of())
                .withGatherSource("minecraft:oak_planks", "oak_planks");

        assertThrows(
                UnplannableGoalException.class,
                () -> ResourceResolver.resolve("minecraft:oak_planks", 67, knowledge),
                "Пространство имён не делает доски другим блоком: 'minecraft:oak_planks' и 'oak_planks' — "
                        + "один предмет, и добыча размещённых досок запрещена так же, как для короткого id (A)");
    }

    @Test
    void resolve_DropSourcedCraftedFamily_GathersSourceBlock() {
        ResourceKnowledge knowledge =
                new StubKnowledge(Set.of("glass"), Map.of(), Map.of()).withGatherSource("glass", "sand");

        TaskNode plan = ResourceResolver.resolve("glass", 3, knowledge);

        assertEquals(
                gather("sand", 3),
                plan,
                "Предмет с настоящим блоком-источником по-прежнему берётся из мира: правило крафтовых семейств "
                        + "не должно отменять добычу сырья");
    }

    @Test
    void resolve_NaturalBlock_StillGathersFromWorld() {
        ResourceKnowledge knowledge = new StubKnowledge(Set.of("dirt"), Map.of(), Map.of());

        TaskNode plan = ResourceResolver.resolve("dirt", 5, knowledge);

        assertEquals(
                gather("dirt", 5),
                plan,
                "Природный блок остаётся добычей: правило не должно запрещать сбор земли, песка и камня");
    }

    @Test
    void isCrafted_Families_True() {
        for (String item : List.of(PLANKS, SAPLING, "oak_fence", "oak_stairs", "oak_door", "torch", "chest")) {
            assertTrue(
                    CraftedItems.isCrafted(item),
                    "Предмет '" + item + "' делается крафтом: добыча его размещённых копий — та же ошибка, "
                            + "что с досками и саженцами");
        }
    }

    @Test
    void isCrafted_NaturalItems_False() {
        for (String item : List.of("oak_log", "dirt", "stone", "coal_ore", "raw_iron", "oak_leaves")) {
            assertFalse(
                    CraftedItems.isCrafted(item),
                    "Природный '" + item + "' не крафтовый: запрет сбора сломал бы добычу сырья");
        }
    }

    private static ScriptCall gather(String block, int count) {
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }

    private static ScriptCall craft(String item, int count) {
        return new ScriptCall("craft", Map.of("item", item, "count", count, "radius", STATION_RADIUS, "place", false));
    }
}

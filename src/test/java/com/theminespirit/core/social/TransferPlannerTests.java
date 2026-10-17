package com.theminespirit.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.Ingredient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class TransferPlannerTests {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final BlockCoord HOME = new BlockCoord(0, 64, 0);
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit NOTHING_PROTECTED = BaseKit.parse(
            "{\"rules\":[{\"match\":[\"*\"],\"keep\":0,\"mode\":\"any\"}],\"never_give_away\":[\"shulker_box\"]}",
            "тестовый набор");
    private static final BaseKit KEEP_TWO_STICKS = BaseKit.parse(
            "{\"rules\":[{\"match\":[\"stick\"],\"keep\":2,\"mode\":\"any\"}],\"never_give_away\":[\"shulker_box\"]}",
            "тестовый набор");

    private static TransferSource source(UUID id, BlockCoord at, BaseKit kit, Stack... stacks) {
        return new TransferSource(id, at, List.of(stacks), kit, List.of());
    }

    private static Stack sticks(int count) {
        return new Stack("stick", 0, count, 64);
    }

    private static Stack cobble(int count) {
        return new Stack("cobblestone", 0, count, 64);
    }

    @Test
    void plan_SourceWithSurplus_GivesOnlyAboveNorm() {
        TransferNeed need = new TransferNeed(B, "stick", 4, HOME);
        List<TransferOrder> orders = TransferPlanner.plan(List.of(need),
                List.of(source(A, HOME, KEEP_TWO_STICKS, sticks(6))), Map.of(), VALUES, 32, 64);

        assertEquals(1, orders.size(), "Один источник с излишком обязан дать ровно одну передачу");
        assertEquals(4, orders.get(0).count(),
                "Из шести палок две держит норма, а четыре — излишек: передают нужное, но не норму");
        assertEquals(Optional.of(A), orders.get(0).from(), "Передачу собирают с духа, у которого излишек");
    }

    @Test
    void plan_EmptySlotInSource_SurplusStillCounted() {
        TransferSource withEmptySlot = new TransferSource(A, HOME, Arrays.asList(sticks(3), null),
                NOTHING_PROTECTED, List.of());
        TransferNeed need = new TransferNeed(B, "stick", 2, HOME);

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(withEmptySlot), Map.of(),
                VALUES, 32, 64);

        assertEquals(1, orders.size(), "Пустой слот инвентаря не мешает передаче: излишек считается по непустым");
        assertEquals(2, orders.get(0).count(), "Из трёх палок передают две нужные");
    }

    @Test
    void plan_NormOnly_NoOrders() {
        TransferNeed need = new TransferNeed(B, "stick", 4, HOME);
        List<TransferOrder> orders = TransferPlanner.plan(List.of(need),
                List.of(source(A, HOME, KEEP_TWO_STICKS, sticks(2))), Map.of(), VALUES, 32, 64);

        assertTrue(orders.isEmpty(), "Палки в пределах нормы не отдают: норма неприкосновенна");
    }

    @Test
    void plan_LargestSurplus_IsChosenFirst() {
        TransferNeed need = new TransferNeed(C, "stick", 5, HOME);
        TransferSource large = source(A, new BlockCoord(4, 64, 0), NOTHING_PROTECTED, sticks(8));
        TransferSource small = source(B, new BlockCoord(1, 64, 0), NOTHING_PROTECTED, sticks(3));

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(small, large), Map.of(),
                VALUES, 32, 64);

        assertEquals(Optional.of(A), orders.get(0).from(),
                "Первым берут источник с наибольшим излишком: близость не важнее запаса");
        assertEquals(5, orders.get(0).count(), "Передают ровно нужное, даже если излишка больше");
    }

    @Test
    void plan_NoSourcesAndEmptyWarehouse_NoOrders() {
        TransferNeed need = new TransferNeed(B, "stick", 2, HOME);

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(), Map.of(), VALUES, 32, 64);

        assertTrue(orders.isEmpty(), "Нет источников — нет задач: доска не выдаёт пустую передачу");
    }

    @Test
    void plan_Warehouse_IsFallbackSource() {
        TransferNeed need = new TransferNeed(B, "cobblestone", 3, HOME);

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(), Map.of("cobblestone", 7),
                VALUES, 32, 64);

        assertEquals(1, orders.size(), "Склад обязан стать источником, если у духов излишка нет");
        assertTrue(orders.get(0).fromWarehouse(), "Со склада забирают через withdraw, а не доставкой духа");
        assertEquals(3, orders.get(0).count(), "Со склада берут ровно нужное количество");
    }

    @Test
    void plan_SourceOutsideRadius_IsIgnored() {
        TransferNeed need = new TransferNeed(B, "stick", 2, HOME);
        TransferSource far = source(A, new BlockCoord(50, 64, 0), NOTHING_PROTECTED, sticks(8));

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(far), Map.of(), VALUES, 16, 64);

        assertTrue(orders.isEmpty(), "Источник за радиусом не участвует: дальняя передача не планируется");
    }

    @Test
    void plan_SameSurplus_NearestWins() {
        TransferNeed need = new TransferNeed(C, "stick", 2, HOME);
        TransferSource near = source(A, new BlockCoord(3, 64, 0), NOTHING_PROTECTED, sticks(2));
        TransferSource farther = source(B, new BlockCoord(9, 64, 0), NOTHING_PROTECTED, sticks(2));

        List<TransferOrder> orders = TransferPlanner.plan(List.of(need), List.of(farther, near), Map.of(),
                VALUES, 32, 64);

        assertEquals(Optional.of(A), orders.get(0).from(),
                "При равном излишке берут ближайшего: путь короче — передача быстрее");
    }

    @Test
    void planForCraft_MissingIngredient_TransferFromHolder() {
        List<Ingredient> recipe = List.of(new Ingredient("stick", 2), new Ingredient("cobblestone", 3));

        List<TransferOrder> orders = TransferPlanner.planForCraft("stone_pickaxe", B, HOME,
                List.of(cobble(3)), recipe,
                List.of(source(A, new BlockCoord(2, 64, 0), NOTHING_PROTECTED, sticks(5))), Map.of(),
                VALUES, 32, 64);

        assertEquals(1, orders.size(), "Нехватка одного ингредиента даёт одну передачу нужного предмета");
        assertEquals("stick", orders.get(0).item(), "Передают тот ингредиент, которого крафтеру не хватает");
        assertEquals(2, orders.get(0).count(), "Передают ровно нехватку рецепта");
        assertEquals(Optional.of(A), orders.get(0).from(), "Источник — дух с излишком палок");
    }

    @Test
    void planForCraft_IngredientPresent_NoOrders() {
        List<TransferOrder> orders = TransferPlanner.planForCraft("stone_pickaxe", B, HOME,
                List.of(cobble(3), sticks(2)), List.of(new Ingredient("stick", 2), new Ingredient("cobblestone", 3)),
                List.of(source(A, HOME, NOTHING_PROTECTED, sticks(5))), Map.of(), VALUES, 32, 64);

        assertTrue(orders.isEmpty(), "Все ингредиенты на месте — передача не нужна");
    }

    @Property
    void plan_AnyCount_NeverExceedsNeedOrSurplus(@ForAll @IntRange(min = 1, max = 40) int owned,
            @ForAll @IntRange(min = 1, max = 40) int needed) {
        TransferNeed need = new TransferNeed(B, "stick", needed, HOME);
        List<TransferOrder> orders = TransferPlanner.plan(List.of(need),
                List.of(source(A, HOME, NOTHING_PROTECTED, sticks(owned))), Map.of(), VALUES, 32, 64);

        if (orders.isEmpty()) {
            assertFalse(owned > 0 && needed > 0, "При излишке и нужде передача обязана существовать");
            return;
        }
        assertEquals(Math.min(owned, needed), orders.get(0).count(),
                "Передача обязана покрыть нужду в пределах излишка: owned=" + owned + ", needed=" + needed);
    }
}

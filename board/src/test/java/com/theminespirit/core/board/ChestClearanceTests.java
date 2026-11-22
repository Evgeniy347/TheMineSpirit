package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestClearanceTests {
    private static final BlockCoord CHEST = new BlockCoord(10, 64, 10);
    private static final int SLOTS = 27;

    @Test
    void decide_EmptyExtraChest_CanBeBroken() {
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(), false, List.of(empty()));

        assertEquals(
                ChestClearance.Action.REMOVE,
                decision.action(),
                "Пустой сундук ломают без переноса"
                        + " Иначе тест decide_EmptyExtraChest_CanBeBroken перестанет ловить эту поломку. При смене decide_EmptyExtraChest_CanBeBroken проверь это утверждение.");
        assertTrue(
                decision.moves().isEmpty(),
                "Из пустого сундука нечего выносить"
                        + " Иначе тест decide_EmptyExtraChest_CanBeBroken перестанет ловить эту поломку. При смене decide_EmptyExtraChest_CanBeBroken проверь это утверждение.");
    }

    @Test
    void decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak() {
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(dirt(5), log(3)), false, List.of(empty()));

        assertEquals(
                ChestClearance.Action.DRAIN,
                decision.action(),
                "Непустой сундук сначала выносят в склад"
                        + " Иначе тест decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak перестанет ловить эту поломку. При смене decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak проверь это утверждение.");
        assertEquals(
                2,
                decision.neededSlots(),
                "Пачка грязи и пачка брёвен занимают по слоту — 2 слота"
                        + " Иначе тест decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak перестанет ловить эту поломку. При смене decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak проверь это утверждение.");
        assertEquals(
                2,
                decision.moves().size(),
                "В плане переноса оба предмета"
                        + " Иначе тест decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak перестанет ловить эту поломку. При смене decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak проверь это утверждение.");
    }

    @Test
    void decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking() {
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(dirt(5)), false, List.of(full()));

        assertEquals(
                ChestClearance.Action.WAIT,
                decision.action(),
                "Полный склад и нет досок — ждём, сундук не ломаем"
                        + " Иначе тест decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking перестанет ловить эту поломку. При смене decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking проверь это утверждение.");
        assertFalse(
                decision.breaks(),
                "WAIT не даёт права ломать непустой сундук"
                        + " Иначе тест decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking перестанет ловить эту поломку. При смене decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking проверь это утверждение.");
    }

    @Test
    void decide_ChestInsideWarehousePlan_IsKept() {
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(dirt(5)), true, List.of(empty()));

        assertEquals(
                ChestClearance.Action.KEEP,
                decision.action(),
                "Сундук в клетке плана склада не снос"
                        + " Иначе тест decide_ChestInsideWarehousePlan_IsKept перестанет ловить эту поломку. При смене decide_ChestInsideWarehousePlan_IsKept проверь это утверждение.");
    }

    @Test
    void decide_NoBuiltWarehouse_WaitsEvenIfEmpty() {
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(dirt(5)), false, List.of());

        assertEquals(
                ChestClearance.Action.WAIT,
                decision.action(),
                "Без построенного склада вещи выносить некуда: сундук ждёт первого склада"
                        + " Иначе тест decide_NoBuiltWarehouse_WaitsEvenIfEmpty перестанет ловить эту поломку. При смене decide_NoBuiltWarehouse_WaitsEvenIfEmpty проверь это утверждение.");
        assertFalse(
                decision.breaks(),
                "WAIT не даёт права ломать непустой сундук без склада"
                        + " Иначе тест decide_NoBuiltWarehouse_WaitsEvenIfEmpty перестанет ловить эту поломку. При смене decide_NoBuiltWarehouse_WaitsEvenIfEmpty проверь это утверждение.");
    }

    @Test
    void neededSlots_PartialStacksInWarehouse_CountsOnlyRemainder() {
        ContainerSnapshot warehouse = snapshot(dirt(60));

        int needed = ChestClearance.neededSlots(snapshot(dirt(64), dirt(64), dirt(1)), List.of(warehouse));

        assertEquals(
                2,
                needed,
                "В складе 4 свободных места в стопке грязи, остальные 125 — два слота"
                        + " Иначе тест neededSlots_PartialStacksInWarehouse_CountsOnlyRemainder перестанет ловить эту поломку. При смене neededSlots_PartialStacksInWarehouse_CountsOnlyRemainder проверь это утверждение.");
    }

    @Test
    void moves_TotalCountsByItem_ArePreserved() {
        ContainerSnapshot chest = snapshot(dirt(5), dirt(7), log(3));

        List<ChestClearance.Move> moves = ChestClearance.moves(chest);

        assertEquals(
                2,
                moves.size(),
                "Две грязи складываются в один перенос, бревно — второй"
                        + " Иначе тест moves_TotalCountsByItem_ArePreserved перестанет ловить эту поломку. При смене moves_TotalCountsByItem_ArePreserved проверь это утверждение.");
        assertEquals(
                12,
                moves.stream()
                        .filter(move -> "dirt".equals(move.item()))
                        .findFirst()
                        .orElseThrow()
                        .count(),
                "Число грязи при переносе сохраняется"
                        + " Иначе тест moves_TotalCountsByItem_ArePreserved перестанет ловить эту поломку. При смене moves_TotalCountsByItem_ArePreserved проверь это утверждение.");
        assertEquals(
                3,
                moves.stream()
                        .filter(move -> "oak_log".equals(move.item()))
                        .findFirst()
                        .orElseThrow()
                        .count(),
                "Число брёвен при переносе сохраняется"
                        + " Иначе тест moves_TotalCountsByItem_ArePreserved перестанет ловить эту поломку. При смене moves_TotalCountsByItem_ArePreserved проверь это утверждение.");
    }

    private static ContainerSnapshot empty() {
        return snapshot();
    }

    private static ContainerSnapshot full() {
        List<Stack> slots = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.add(new Stack("cobblestone", 0, 64, 64));
        }
        return new ContainerSnapshot(CHEST, slots);
    }

    private static Stack dirt(int count) {
        return new Stack("dirt", 0, count, 64);
    }

    private static Stack log(int count) {
        return new Stack("oak_log", 0, count, 64);
    }

    private static ContainerSnapshot snapshot(Stack... stacks) {
        List<Stack> slots = new ArrayList<>(SLOTS);
        for (int slot = 0; slot < SLOTS; slot++) {
            slots.add(null);
        }
        for (int index = 0; index < stacks.length; index++) {
            slots.set(index, stacks[index]);
        }
        return new ContainerSnapshot(CHEST, slots);
    }
}

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
        ChestClearance.Decision decision = ChestClearance.decide(snapshot(), false, List.of(empty()), List.of(), 0);

        assertEquals(ChestClearance.Action.REMOVE, decision.action(), "Пустой сундук ломают без переноса");
        assertTrue(decision.moves().isEmpty(), "Из пустого сундука нечего выносить");
    }

    @Test
    void decide_NonEmptyAndWarehouseHasRoom_DrainsBeforeBreak() {
        ChestClearance.Decision decision =
                ChestClearance.decide(snapshot(dirt(5), log(3)), false, List.of(empty()), List.of(), 0);

        assertEquals(ChestClearance.Action.DRAIN, decision.action(), "Непустой сундук сначала выносят в склад");
        assertEquals(2, decision.neededSlots(), "Пачка грязи и пачка брёвен занимают по слоту — 2 слота");
        assertEquals(2, decision.moves().size(), "В плане переноса оба предмета");
    }

    @Test
    void decide_NonEmptyAndWarehouseFull_WaitsInsteadOfBreaking() {
        ChestClearance.Decision decision =
                ChestClearance.decide(snapshot(dirt(5)), false, List.of(full()), List.of(), 0);

        assertEquals(
                ChestClearance.Action.WAIT, decision.action(), "Полный склад и нет досок — ждём, сундук не ломаем");
        assertFalse(decision.breaks(), "WAIT не даёт права ломать непустой сундук");
    }

    @Test
    void decide_NonEmptyFullWarehouseWithMaterials_AddsChestFirst() {
        BlockCoord cell = new BlockCoord(20, 64, 20);
        ChestClearance.Decision decision =
                ChestClearance.decide(snapshot(dirt(5)), false, List.of(full()), List.of(cell), 8);

        assertEquals(
                ChestClearance.Action.ADD_CHEST,
                decision.action(),
                "Склад полон: сначала ставим сундук в клетку склада, потом выносим вещи");
    }

    @Test
    void decide_ChestInsideWarehousePlan_IsKept() {
        ChestClearance.Decision decision =
                ChestClearance.decide(snapshot(dirt(5)), true, List.of(empty()), List.of(), 0);

        assertEquals(ChestClearance.Action.KEEP, decision.action(), "Сундук в клетке плана склада не снос");
    }

    @Test
    void neededSlots_PartialStacksInWarehouse_CountsOnlyRemainder() {
        ContainerSnapshot warehouse = snapshot(dirt(60));

        int needed = ChestClearance.neededSlots(snapshot(dirt(64), dirt(64), dirt(1)), List.of(warehouse));

        assertEquals(2, needed, "В складе 4 свободных места в стопке грязи, остальные 125 — два слота");
    }

    @Test
    void moves_TotalCountsByItem_ArePreserved() {
        ContainerSnapshot chest = snapshot(dirt(5), dirt(7), log(3));

        List<ChestClearance.Move> moves = ChestClearance.moves(chest);

        assertEquals(2, moves.size(), "Две грязи складываются в один перенос, бревно — второй");
        assertEquals(
                12,
                moves.stream()
                        .filter(move -> "dirt".equals(move.item()))
                        .findFirst()
                        .orElseThrow()
                        .count(),
                "Число грязи при переносе сохраняется");
        assertEquals(
                3,
                moves.stream()
                        .filter(move -> "oak_log".equals(move.item()))
                        .findFirst()
                        .orElseThrow()
                        .count(),
                "Число брёвен при переносе сохраняется");
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

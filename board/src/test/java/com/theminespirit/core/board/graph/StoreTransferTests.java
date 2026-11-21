package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreTransferTests {
    private static final GraphFacts.Position FIRST = new GraphFacts.Position(1, 64, 1);
    private static final GraphFacts.Position SECOND = new GraphFacts.Position(3, 64, 1);

    @Test
    void chestMove_FullChestAndFreeChest_MovesMostCommonItem() {
        StoreTransfer.Move move = StoreTransfer.chestMove(List.of(
                        chest(FIRST, Map.of("minecraft:oak_log", 40, "minecraft:coal", 24), 0),
                        chest(SECOND, Map.of(), 27)))
                .orElseThrow();

        assertEquals(
                FIRST,
                move.from(),
                "Источник — заполненный сундук"
                        + " Иначе тест chestMove_FullChestAndFreeChest_MovesMostCommonItem перестанет ловить эту поломку. При смене chestMove_FullChestAndFreeChest_MovesMostCommonItem проверь это утверждение.");
        assertEquals(
                SECOND,
                move.to(),
                "Приёмник — свободный сундук"
                        + " Иначе тест chestMove_FullChestAndFreeChest_MovesMostCommonItem перестанет ловить эту поломку. При смене chestMove_FullChestAndFreeChest_MovesMostCommonItem проверь это утверждение.");
        assertEquals(
                "minecraft:oak_log",
                move.item(),
                "Переносят самый частый предмет"
                        + " Иначе тест chestMove_FullChestAndFreeChest_MovesMostCommonItem перестанет ловить эту поломку. При смене chestMove_FullChestAndFreeChest_MovesMostCommonItem проверь это утверждение.");
        assertEquals(
                16,
                move.count(),
                "За раз переносят ограниченную партию"
                        + " Иначе тест chestMove_FullChestAndFreeChest_MovesMostCommonItem перестанет ловить эту поломку. При смене chestMove_FullChestAndFreeChest_MovesMostCommonItem проверь это утверждение.");
    }

    @Test
    void chestMove_NoFullChest_Empty() {
        assertTrue(
                StoreTransfer.chestMove(
                                List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 5), chest(SECOND, Map.of(), 27)))
                        .isEmpty(),
                "Пока сундук не заполнен, перестановка не нужна"
                        + " Иначе тест chestMove_NoFullChest_Empty перестанет ловить эту поломку. При смене chestMove_NoFullChest_Empty проверь это утверждение.");
    }

    @Test
    void chestMove_OnlyFullChests_Empty() {
        assertTrue(
                StoreTransfer.chestMove(List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 0)))
                        .isEmpty(),
                "Из полного сундука некуда переставить: нужен свободный"
                        + " Иначе тест chestMove_OnlyFullChests_Empty перестанет ловить эту поломку. При смене chestMove_OnlyFullChests_Empty проверь это утверждение.");
    }

    @Test
    void hasFreeChest_AnyFreeSlot_True() {
        assertTrue(
                StoreTransfer.hasFreeChest(
                        List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 0), chest(SECOND, Map.of(), 1))),
                "Один свободный слот — уже место для несомого"
                        + " Иначе тест hasFreeChest_AnyFreeSlot_True перестанет ловить эту поломку. При смене hasFreeChest_AnyFreeSlot_True проверь это утверждение.");
        assertFalse(
                StoreTransfer.hasFreeChest(List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 0))),
                "Заполненный склад не принимает несомое"
                        + " Иначе тест hasFreeChest_AnyFreeSlot_True перестанет ловить эту поломку. При смене hasFreeChest_AnyFreeSlot_True проверь это утверждение.");
    }

    @Test
    void move_InvalidItem_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new StoreTransfer.Move(FIRST, SECOND, "  ", 1),
                "Перенос без предмета обязан быть отвергнут конструктором"
                        + " Иначе тест move_InvalidItem_Rejected перестанет ловить эту поломку. При смене move_InvalidItem_Rejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("Предмет"),
                "Ошибка называет пустой предмет переноса"
                        + " Иначе тест move_InvalidItem_Rejected перестанет ловить эту поломку. При смене move_InvalidItem_Rejected проверь это утверждение.");
    }

    private static GraphFacts.Chest chest(GraphFacts.Position position, Map<String, Integer> contents, int free) {
        return new GraphFacts.Chest(position, contents, free, free + contents.size(), GraphFacts.Reach.UNKNOWN);
    }
}

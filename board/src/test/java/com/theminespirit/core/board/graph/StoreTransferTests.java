package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StoreTransferTests {
    private static final GraphFacts.Position FIRST = new GraphFacts.Position(1, 64, 1);
    private static final GraphFacts.Position SECOND = new GraphFacts.Position(3, 64, 1);

    @Test
    void chestMove_FullChestAndFreeChest_MovesMostCommonItem() {
        StoreTransfer.Move move = StoreTransfer.chestMove(
                        List.of(
                                chest(FIRST, Map.of("minecraft:oak_log", 40, "minecraft:coal", 24), 0),
                                chest(SECOND, Map.of(), 27)),
                        Set.of(FIRST, SECOND))
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
    void chestMove_WarehouseOnly_IgnoresFreeTemporaryChest() {
        GraphFacts.Position warehouse = new GraphFacts.Position(5, 64, 1);

        StoreTransfer.Move move = StoreTransfer.chestMove(
                        List.of(
                                chest(FIRST, Map.of("minecraft:oak_log", 40), 0),
                                chest(SECOND, Map.of(), 27),
                                chest(warehouse, Map.of(), 20)),
                        Set.of(warehouse))
                .orElseThrow();

        assertEquals(
                warehouse,
                move.to(),
                "Приёмник ограничен клетками склада: временный сундук не должен перехватывать перенос, иначе "
                        + "временные сундуки будут бесконечно перекладываться друг в друга и склад останется пустым. "
                        + "При смене StoreTransfer.chestMove проверь StorePost.transfer и живой перенос temp→warehouse.");
    }

    @Test
    void chestMove_RemoteSourceWithoutPath_MovesToPhysicalWarehouse() {
        StoreTransfer.Move move = StoreTransfer.chestMove(
                        List.of(
                                chest(FIRST, Map.of("minecraft:oak_log", 40), 0, GraphFacts.Reach.NO_PATH, true),
                                chest(SECOND, Map.of(), 27, GraphFacts.Reach.PATH, false)),
                        Set.of(SECOND))
                .orElseThrow();

        assertEquals(
                FIRST,
                move.from(),
                "Источник без пути выбирается, если получение из него дистанционное: иначе временный сундук "
                        + "никогда не опустеет. При смене chestMove_RemoteSourceWithoutPath_MovesToPhysicalWarehouse "
                        + "проверь StorePost.transfer и WithdrawScript.");
        assertEquals(
                SECOND,
                move.to(),
                "Приёмник остаётся физически достижимым: дистанционная выгрузка запрещена. Иначе перенос "
                        + "зависнет в сундуке без пути. При смене "
                        + "chestMove_RemoteSourceWithoutPath_MovesToPhysicalWarehouse проверь ActivityPlans.moveItem.");
    }

    @Test
    void chestMove_SourceWithoutPathOrRemote_IsSkipped() {
        assertTrue(
                StoreTransfer.chestMove(
                                List.of(
                                        chest(
                                                FIRST,
                                                Map.of("minecraft:oak_log", 40),
                                                0,
                                                GraphFacts.Reach.NO_PATH,
                                                false),
                                        chest(SECOND, Map.of(), 27, GraphFacts.Reach.PATH, false)),
                                Set.of(SECOND))
                        .isEmpty(),
                "Обычный сундук без пути не становится источником переноса: иначе граф выдаёт невыполнимую "
                        + "задачу. При смене chestMove_SourceWithoutPathOrRemote_IsSkipped проверь "
                        + "GraphFacts.Chest.retrievable и StoreTransfer.");
    }

    @Test
    void chestMove_RemoteDestinationWithoutPath_RejectsUnload() {
        assertTrue(
                StoreTransfer.chestMove(
                                List.of(
                                        chest(FIRST, Map.of("minecraft:oak_log", 40), 0),
                                        chest(SECOND, Map.of(), 27, GraphFacts.Reach.NO_PATH, true)),
                                Set.of(SECOND))
                        .isEmpty(),
                "Дистанционное получение не превращается в дистанционную выгрузку: сундук без пути не может "
                        + "быть приёмником. Иначе перенос зависнет в запрещённой операции. При смене "
                        + "chestMove_RemoteDestinationWithoutPath_RejectsUnload проверь StoreTransfer и DepositScript.");
    }

    @Test
    void chestMove_NoFullChest_Empty() {
        assertTrue(
                StoreTransfer.chestMove(
                                List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 5), chest(SECOND, Map.of(), 27)),
                                Set.of(FIRST, SECOND))
                        .isEmpty(),
                "Пока сундук не заполнен, перестановка не нужна"
                        + " Иначе тест chestMove_NoFullChest_Empty перестанет ловить эту поломку. При смене chestMove_NoFullChest_Empty проверь это утверждение.");
    }

    @Test
    void chestMove_OnlyFullChests_Empty() {
        assertTrue(
                StoreTransfer.chestMove(List.of(chest(FIRST, Map.of("minecraft:oak_log", 40), 0)), Set.of(FIRST))
                        .isEmpty(),
                "Из полного сундука некуда переставить: нужен свободный"
                        + " Иначе тест chestMove_OnlyFullChests_Empty перестанет ловить эту поломку. При смене chestMove_OnlyFullChests_Empty проверь это утверждение.");
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
        return chest(position, contents, free, GraphFacts.Reach.UNKNOWN, false);
    }

    private static GraphFacts.Chest chest(
            GraphFacts.Position position,
            Map<String, Integer> contents,
            int free,
            GraphFacts.Reach reach,
            boolean remote) {
        return new GraphFacts.Chest(position, contents, free, free + contents.size(), reach, remote);
    }
}

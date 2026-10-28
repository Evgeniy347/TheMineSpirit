package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ToolGateTests {
    @Test
    void allows_ClearWithoutPickaxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:wooden_shovel", "minecraft:stone_sword")),
                "Расчистка с выравниванием режет камень: духу без кирки она не выдаётся, а не проваливается «нужен "
                        + "инструмент» (INC-67)");
    }

    @Test
    void allows_ClearWithStonePickaxe_Given() {
        assertTrue(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:stone_pickaxe")),
                "Любая кирка годится для расчистки");
        assertTrue(ToolGate.allows(BoardCase.STOCK_CHESTS, List.of()), "Кейсу без инструмента гейт не мешает");
    }

    @Test
    void table_PickaxeCasesExplicit_ClearMineAndOre() {
        assertEquals(
                Set.of(BoardCase.CLEAR_GROUNDS, BoardCase.BUILD_MINE, BoardCase.MINE_LEFT_ORE),
                ToolGate.table().entrySet().stream()
                        .filter(entry -> entry.getValue() == ToolGate.Hands.PICKAXE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()),
                "Кирку требуют расчистка, шахта и добыча оставшейся руды — таблица ToolGate; новый кейс с камнем добавляется в неё, иначе "
                        + "дух возьмёт его без кирки и будет копать руками без дропа");
    }

    @Test
    void allows_ChopLogWithoutAxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_shovel"), "jungle_log"),
                "Бревно не рубят руками: духу без топора задание рубки не выдаётся, а ждёт топор (правило владельца "
                        + "28.10)");
        assertTrue(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_axe"), "jungle_log"),
                "Любой топор годится для рубки дерева");
        assertTrue(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of(), "dirt"),
                "Землю и прочую добычу без дерева дух берёт руками — топор нужен только бревну");
    }

    @Test
    void keep_ChopLogAxeLost_ReleasedToBoard() {
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_axe"), false, "jungle_log")
                        instanceof ToolGate.Keep,
                "С топором задание рубки остаётся у духа");
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of(), false, "jungle_log") instanceof ToolGate.Release,
                "Потерял топор — задание возвращено доске и ждёт инструмент, а не рубит руками");
    }

    @Test
    void table_FirstWoodenToolsChain_BareHands() {
        for (BoardCase boardCase :
                List.of(BoardCase.GATHER_MATERIALS, BoardCase.CRAFT_WOODEN_PICKAXE, BoardCase.REPAIR_TOOL)) {
            assertEquals(
                    ToolGate.Hands.BARE,
                    ToolGate.table().get(boardCase),
                    "Цепочка к первой кирке идёт голыми руками, иначе без кирки её не сделать: " + boardCase);
            assertTrue(
                    ToolGate.keep(boardCase, List.of(), false) instanceof ToolGate.Keep,
                    "Задание голыми руками не снимается за отсутствие кирки: " + boardCase);
        }
    }
}

package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void allows_DismantleWithoutPickaxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.DISMANTLE_SHELTER, List.of("minecraft:wooden_axe")),
                "Уборка висящего ломает камень/бревно: духу без кирки она не выдаётся, иначе падает «нужна кирка» "
                        + "(observe19 F: demolish FAILED без кирки)");
    }

    @Test
    void allows_ClearWithStonePickaxe_Given() {
        assertTrue(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:stone_pickaxe")),
                "Любая кирка годится для расчистки");
        assertTrue(ToolGate.allows(BoardCase.STOCK_CHESTS, List.of()), "Кейсу без инструмента гейт не мешает");
    }

    @Test
    void table_PickaxeCasesExplicit_ClearHighMineAndOre() {
        assertEquals(
                Set.of(
                        BoardCase.CLEAR_GROUNDS,
                        BoardCase.CLEAR_HIGH,
                        BoardCase.BUILD_MINE,
                        BoardCase.MINE_LEFT_ORE,
                        BoardCase.DISMANTLE_SHELTER),
                ToolGate.table().entrySet().stream()
                        .filter(entry -> entry.getValue() == ToolGate.Hands.PICKAXE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()),
                "Кирку требуют расчистка, шахта и добыча оставшейся руды — таблица ToolGate; новый кейс с камнем добавляется в неё, иначе "
                        + "дух возьмёт его без кирки и будет копать руками без дропа");
    }

    @Test
    void allows_HoeModifier_RequiresHoe() {
        BoardTicket ticket = new BoardTicket(
                "graph:building:wheat_field",
                BoardCase.BUILD_PLACED,
                Optional.empty(),
                60,
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of(),
                List.of(ToolGate.TOOL_MODIFIER + "hoe"));
        assertTrue(
                ToolGate.allows(ticket, List.of("minecraft:wooden_hoe")),
                "Грядка требует тяпку: дух с тяпкой получает задание пахоты");
        assertFalse(
                ToolGate.allows(ticket, List.of("minecraft:stone_pickaxe")),
                "Кирка не заменяет тяпку: без неё грядка не пашется, задание ждёт инструмент, а не падает в воздух");
    }

    @Test
    void allowsFromStock_AxeOnly_TakeBeforeChop() {
        BoardTicket chop = new BoardTicket(
                "graph:chop_jungle_log",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                94,
                0,
                1,
                "graph:chop_jungle_log",
                List.of(),
                Map.of(),
                List.of(ToolGate.TOOL_MODIFIER + "_axe"));
        BoardTicket mine = new BoardTicket(
                "building:mine",
                BoardCase.BUILD_MINE,
                Optional.empty(),
                100,
                0,
                1,
                "building:mine",
                List.of(),
                Map.of(),
                List.of(ToolGate.TOOL_MODIFIER + "_pickaxe"));

        assertTrue(
                ToolGate.allowsFromStock(chop, List.of("minecraft:stone_axe")),
                "Топор в сундуке закрывает рубку: дух берёт его до дерева, а не ждёт крафт");
        assertFalse(
                ToolGate.allowsFromStock(chop, List.of("minecraft:stone_pickaxe")),
                "Кирка не заменяет топор: рубка ждёт именно топор");
        assertFalse(
                ToolGate.allowsFromStock(mine, List.of("minecraft:stone_pickaxe")),
                "Со склада пока берётся только топор: кирку шахтёр получает из рук, иначе задание выдадут "
                        + "духу, который не умеет её взять");
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
    void keep_ChopLogAxeLost_TicketStaysWithSpirit() {
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_axe"), false, "jungle_log")
                        instanceof ToolGate.Keep,
                "С топором задание рубки остаётся у духа");
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of(), false, "jungle_log") instanceof ToolGate.Keep,
                "Топор сломался — задание рубки остаётся у духа и дерево дорубается руками: инструмент — "
                        + "предпочтение (владелец 05.11), а не причина бросить работу и вернуть её доске");
    }

    @Test
    void withTool_LogGather_MarkedAxeButGivenToBareHands() {
        BoardTicket bare = new BoardTicket(
                "graph:chop_jungle_log",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                94,
                0,
                1,
                "graph:chop_jungle_log",
                List.of(),
                Map.of());

        BoardTicket marked = ToolGate.withTool(bare, "jungle_log");

        assertTrue(
                marked.modifiers().contains(ToolGate.TOOL_MODIFIER + ToolGate.AXE),
                "Рубка бревна несёт пометку топора в самом задании: " + marked);
        assertFalse(
                ToolGate.allows(marked, List.of("minecraft:stone_sword", "minecraft:wooden_shovel")),
                "Пометка топора остаётся сведением «топора нет»: по нему выбирают скорость, а не запрет работы");
        assertTrue(
                ToolGate.keep(marked, List.of("minecraft:stone_sword"), false) instanceof ToolGate.Keep,
                "Без топора рубка не снимается с духа: дерево рубят руками, топор — предпочтение (владелец 05.11)");
        assertTrue(ToolGate.allows(marked, List.of("minecraft:stone_axe")), "С топором та же рубка идёт быстрее");
    }

    @Test
    void withTool_DirtGather_NoToolMark() {
        BoardTicket dirt = new BoardTicket(
                "graph:dig_dirt",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                48,
                0,
                1,
                "graph:dig_dirt",
                List.of(),
                Map.of());

        assertEquals(
                List.of(),
                ToolGate.withTool(dirt, "dirt").modifiers(),
                "Землю дух берёт руками: пометки инструмента у добычи земли нет — задание не ждёт топора");
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

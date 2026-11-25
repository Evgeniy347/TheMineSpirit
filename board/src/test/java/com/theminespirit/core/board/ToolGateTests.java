package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                        + "инструмент» (INC-67)"
                        + " Иначе тест allows_ClearWithoutPickaxe_NotGiven перестанет ловить эту поломку. При смене allows_ClearWithoutPickaxe_NotGiven проверь это утверждение.");
    }

    @Test
    void allows_DismantleWithoutPickaxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.DISMANTLE_SHELTER, List.of("minecraft:wooden_axe")),
                "Уборка висящего ломает камень/бревно: духу без кирки она не выдаётся, иначе падает «нужна кирка» "
                        + "(observe19 F: demolish FAILED без кирки)"
                        + " При смене allows_DismantleWithoutPickaxe_NotGiven проверь это утверждение.");
    }

    @Test
    void allows_ClearWithStonePickaxe_Given() {
        assertTrue(
                ToolGate.allows(BoardCase.CLEAR_GROUNDS, List.of("minecraft:stone_pickaxe")),
                "Любая кирка годится для расчистки"
                        + " Иначе тест allows_ClearWithStonePickaxe_Given перестанет ловить эту поломку. При смене allows_ClearWithStonePickaxe_Given проверь это утверждение.");
        assertTrue(
                ToolGate.allows(BoardCase.STOCK_CHESTS, List.of()),
                "Кейсу без инструмента гейт не мешает"
                        + " Иначе тест allows_ClearWithStonePickaxe_Given перестанет ловить эту поломку. При смене allows_ClearWithStonePickaxe_Given проверь это утверждение.");
    }

    @Test
    void table_PickaxeCasesExplicit_ClearMineAndOre() {
        assertEquals(
                Set.of(
                        BoardCase.CLEAR_GROUNDS,
                        BoardCase.BUILD_MINE,
                        BoardCase.MINE_LEFT_ORE,
                        BoardCase.DISMANTLE_SHELTER),
                ToolGate.table().entrySet().stream()
                        .filter(entry -> entry.getValue() == ToolGate.Hands.PICKAXE)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()),
                "Кирку требуют расчистка, шахта и добыча оставшейся руды — таблица ToolGate; новый кейс с камнем добавляется в неё, иначе "
                        + "дух возьмёт его без кирки и будет копать руками без дропа"
                        + " При смене table_PickaxeCasesExplicit_ClearMineAndOre проверь это утверждение.");
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
                "Грядка требует тяпку: дух с тяпкой получает задание пахоты"
                        + " Иначе тест allows_HoeModifier_RequiresHoe перестанет ловить эту поломку. При смене allows_HoeModifier_RequiresHoe проверь это утверждение.");
        assertFalse(
                ToolGate.allows(ticket, List.of("minecraft:stone_pickaxe")),
                "Кирка не заменяет тяпку: без неё грядка не пашется, задание ждёт инструмент, а не падает в воздух"
                        + " Иначе тест allows_HoeModifier_RequiresHoe перестанет ловить эту поломку. При смене allows_HoeModifier_RequiresHoe проверь это утверждение.");
    }

    @Test
    void allows_MultipleTools_RequiresHoeAndShovel() {
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
                List.of(ToolGate.TOOLS_MODIFIER + "hoe,shovel"));

        assertEquals(
                List.of("hoe", "shovel"),
                ToolGate.requiredAll(ticket),
                "Схема грядки требует и тяпку, и лопату: список инструментов должен сохранять оба"
                        + " требования Иначе тест allows_MultipleTools_RequiresHoeAndShovel перестанет"
                        + " ловить живой случай wheat_field от 25.11. При смене"
                        + " allows_MultipleTools_RequiresHoeAndShovel проверь ToolGate.requiredAll.");
        assertTrue(
                ToolGate.allows(ticket, List.of("minecraft:wooden_hoe", "minecraft:wooden_shovel")),
                "С двумя инструментами грядка выдаётся: иначе граф не сможет построить поле с пашней"
                        + " и тропой Иначе тест allows_MultipleTools_RequiresHoeAndShovel перестанет"
                        + " ловить эту поломку. При смене allows_MultipleTools_RequiresHoeAndShovel"
                        + " проверь DispatchRound.tooled.");
        assertFalse(
                ToolGate.allows(ticket, List.of("minecraft:wooden_hoe")),
                "Одной тяпки недостаточно: dirt_path требует лопату, и задание должно ждать её,"
                        + " а не падать посреди стройки Иначе тест allows_MultipleTools_RequiresHoeAndShovel"
                        + " перестанет ловить эту поломку. При смене"
                        + " allows_MultipleTools_RequiresHoeAndShovel проверь StagePlacePost.tools.");
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
                "Топор в сундуке закрывает рубку: дух берёт его до дерева, а не ждёт крафт"
                        + " Иначе тест allowsFromStock_AxeOnly_TakeBeforeChop перестанет ловить эту поломку. При смене allowsFromStock_AxeOnly_TakeBeforeChop проверь это утверждение.");
        assertFalse(
                ToolGate.allowsFromStock(chop, List.of("minecraft:stone_pickaxe")),
                "Кирка не заменяет топор: рубка ждёт именно топор"
                        + " Иначе тест allowsFromStock_AxeOnly_TakeBeforeChop перестанет ловить эту поломку. При смене allowsFromStock_AxeOnly_TakeBeforeChop проверь это утверждение.");
        assertFalse(
                ToolGate.allowsFromStock(mine, List.of("minecraft:stone_pickaxe")),
                "Со склада пока берётся только топор: кирку шахтёр получает из рук, иначе задание выдадут "
                        + "духу, который не умеет её взять"
                        + " При смене allowsFromStock_AxeOnly_TakeBeforeChop проверь это утверждение.");
    }

    @Test
    void allows_ChopLogWithoutAxe_NotGiven() {
        assertFalse(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_shovel"), "jungle_log"),
                "Бревно не рубят руками: духу без топора задание рубки не выдаётся, а ждёт топор (правило владельца "
                        + "28.10)"
                        + " Иначе тест allows_ChopLogWithoutAxe_NotGiven перестанет ловить эту поломку. При смене allows_ChopLogWithoutAxe_NotGiven проверь это утверждение.");
        assertTrue(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_axe"), "jungle_log"),
                "Любой топор годится для рубки дерева"
                        + " Иначе тест allows_ChopLogWithoutAxe_NotGiven перестанет ловить эту поломку. При смене allows_ChopLogWithoutAxe_NotGiven проверь это утверждение.");
        assertTrue(
                ToolGate.allows(BoardCase.GATHER_MATERIALS, List.of(), "dirt"),
                "Землю и прочую добычу без дерева дух берёт руками — топор нужен только бревну"
                        + " Иначе тест allows_ChopLogWithoutAxe_NotGiven перестанет ловить эту поломку. При смене allows_ChopLogWithoutAxe_NotGiven проверь это утверждение.");
    }

    @Test
    void keep_ChopLogAxeLost_TicketStaysWithSpirit() {
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of("minecraft:wooden_axe"), false, "jungle_log")
                        instanceof ToolGate.Keep,
                "С топором задание рубки остаётся у духа"
                        + " Иначе тест keep_ChopLogAxeLost_TicketStaysWithSpirit перестанет ловить эту поломку. При смене keep_ChopLogAxeLost_TicketStaysWithSpirit проверь это утверждение.");
        assertTrue(
                ToolGate.keep(BoardCase.GATHER_MATERIALS, List.of(), false, "jungle_log") instanceof ToolGate.Keep,
                "Топор сломался — задание рубки остаётся у духа и дерево дорубается руками: инструмент — "
                        + "предпочтение (владелец 05.11), а не причина бросить работу и вернуть её доске"
                        + " Иначе тест keep_ChopLogAxeLost_TicketStaysWithSpirit перестанет ловить эту поломку. При смене keep_ChopLogAxeLost_TicketStaysWithSpirit проверь это утверждение.");
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
                "Рубка бревна несёт пометку топора в самом задании: " + marked
                        + " Иначе тест withTool_LogGather_MarkedAxeButGivenToBareHands перестанет ловить эту поломку. При смене withTool_LogGather_MarkedAxeButGivenToBareHands проверь это утверждение.");
        assertFalse(
                ToolGate.allows(marked, List.of("minecraft:stone_sword", "minecraft:wooden_shovel")),
                "Пометка топора остаётся сведением «топора нет»: по нему выбирают скорость, а не запрет работы"
                        + " Иначе тест withTool_LogGather_MarkedAxeButGivenToBareHands перестанет ловить эту поломку. При смене withTool_LogGather_MarkedAxeButGivenToBareHands проверь это утверждение.");
        assertTrue(
                ToolGate.keep(marked, List.of("minecraft:stone_sword"), false) instanceof ToolGate.Keep,
                "Без топора рубка не снимается с духа: дерево рубят руками, топор — предпочтение (владелец 05.11)"
                        + " Иначе тест withTool_LogGather_MarkedAxeButGivenToBareHands перестанет ловить эту поломку. При смене withTool_LogGather_MarkedAxeButGivenToBareHands проверь это утверждение.");
        assertTrue(
                ToolGate.allows(marked, List.of("minecraft:stone_axe")),
                "С топором та же рубка идёт быстрее"
                        + " Иначе тест withTool_LogGather_MarkedAxeButGivenToBareHands перестанет ловить эту поломку. При смене withTool_LogGather_MarkedAxeButGivenToBareHands проверь это утверждение.");
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
                "Землю дух берёт руками: пометки инструмента у добычи земли нет — задание не ждёт топора"
                        + " Иначе тест withTool_DirtGather_NoToolMark перестанет ловить эту поломку. При смене withTool_DirtGather_NoToolMark проверь это утверждение.");
    }

    @Test
    void table_FirstWoodenToolsChain_BareHands() {
        for (BoardCase boardCase :
                List.of(BoardCase.GATHER_MATERIALS, BoardCase.CRAFT_WOODEN_PICKAXE, BoardCase.REPAIR_TOOL)) {
            assertEquals(
                    ToolGate.Hands.BARE,
                    ToolGate.table().get(boardCase),
                    "Цепочка к первой кирке идёт голыми руками, иначе без кирки её не сделать: " + boardCase
                            + " При смене table_FirstWoodenToolsChain_BareHands проверь это утверждение.");
            assertTrue(
                    ToolGate.keep(boardCase, List.of(), false) instanceof ToolGate.Keep,
                    "Задание голыми руками не снимается за отсутствие кирки: " + boardCase
                            + " Иначе тест table_FirstWoodenToolsChain_BareHands перестанет ловить эту поломку. При смене table_FirstWoodenToolsChain_BareHands проверь это утверждение.");
        }
    }

    @Test
    void worn_OnlyWornTool_RefusesUntilRepair() {
        BoardTicket mine = new BoardTicket(
                "graph:building:mine",
                BoardCase.BUILD_MINE,
                Optional.empty(),
                100,
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        List<String> worn = List.of("minecraft:wooden_pickaxe");

        assertTrue(
                ToolGate.worn(mine, worn, worn),
                "Изношенная кирка не идёт в шахту: её сначала чинит или меняет граф, иначе она сломается в работе"
                        + " При смене worn_OnlyWornTool_RefusesUntilRepair проверь это утверждение.");
        assertFalse(
                ToolGate.worn(mine, List.of("minecraft:wooden_pickaxe", "minecraft:stone_pickaxe"), worn),
                "Рядом целая кирка — износ одной не запрещает работу"
                        + " Иначе тест worn_OnlyWornTool_RefusesUntilRepair перестанет ловить эту поломку. При смене worn_OnlyWornTool_RefusesUntilRepair проверь это утверждение.");
        assertFalse(
                ToolGate.worn(mine, List.of("minecraft:stone_sword"), List.of()),
                "Отсутствие кирки — не износ: его закрывает allows, а не этот гейт"
                        + " Иначе тест worn_OnlyWornTool_RefusesUntilRepair перестанет ловить эту поломку. При смене worn_OnlyWornTool_RefusesUntilRepair проверь это утверждение.");
    }
}

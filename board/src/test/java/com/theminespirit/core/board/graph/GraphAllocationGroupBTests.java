package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.candidates;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.crafts;
import static com.theminespirit.core.board.graph.AllocationCases.facts;
import static com.theminespirit.core.board.graph.AllocationCases.only;
import static com.theminespirit.core.board.graph.AllocationCases.plan;
import static com.theminespirit.core.board.graph.AllocationCases.spirit;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphAllocationGroupBTests {
    @Test
    void case11_AxeInChest_EquipInsteadOfCraft() {
        GraphWorld world = community(1);
        world.tools("spirit-1", -1);
        world.facts = facts(List.of(spirit("spirit-1", List.of())), Map.of("minecraft:stone_axe", 1));
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("gear_equip_best")),
                "Топор лежит в сундуке — дух идёт надевать его: " + result.post()
                        + " Иначе тест case11_AxeInChest_EquipInsteadOfCraft перестанет ловить эту поломку. При смене case11_AxeInChest_EquipInsteadOfCraft проверь это утверждение.");
        assertFalse(
                result.post().stream()
                        .anyMatch(ticket ->
                                ticket.id().contains("_axe") && ticket.id().startsWith("graph:craft")),
                "Топор есть в сундуке — новый топор не крафтится: " + result.post()
                        + " Иначе тест case11_AxeInChest_EquipInsteadOfCraft перестанет ловить эту поломку. При смене case11_AxeInChest_EquipInsteadOfCraft проверь это утверждение.");
        assertFalse(
                crafts(result, "gear_craft_axe"),
                "Событие крафта топора не поднимается, пока топор лежит в сундуке: " + result.post()
                        + " Иначе тест case11_AxeInChest_EquipInsteadOfCraft перестанет ловить эту поломку. При смене case11_AxeInChest_EquipInsteadOfCraft проверь это утверждение.");
    }

    @Test
    void case12_AxesInChestsCoverSpirits_NoAxeCraftEver() {
        GraphWorld world = community(3);
        for (int index = 1; index <= 3; index++) {
            world.tools("spirit-" + index, -1);
        }
        world.facts = facts(
                List.of(spirit("spirit-1", List.of()), spirit("spirit-2", List.of()), spirit("spirit-3", List.of())),
                Map.of("minecraft:stone_axe", 20));
        GraphTickets.Result result = tickets(world);

        assertFalse(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("_axe")),
                "Топоров в сундуках больше, чем духов, — крафт топора не ставится никогда (живой случай: 20+): "
                        + result.post()
                        + " Иначе тест case12_AxesInChestsCoverSpirits_NoAxeCraftEver перестанет ловить эту поломку. При смене case12_AxesInChestsCoverSpirits_NoAxeCraftEver проверь это утверждение.");
        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("gear_equip_best")),
                "Вместо крафта духи надевают топоры из сундука: " + result.post()
                        + " Иначе тест case12_AxesInChestsCoverSpirits_NoAxeCraftEver перестанет ловить эту поломку. При смене case12_AxesInChestsCoverSpirits_NoAxeCraftEver проверь это утверждение.");
    }

    @Test
    void case13_NoPickaxe_PickaxeCraftBeforeMine() {
        GraphWorld world = community(1).with("cobblestone", 0).with("stick", 20);
        world.tools("spirit-1", -1);
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:craft_wooden_pickaxe@")),
                "Кирки нет — крафт кирки стоит заданием: " + result.post()
                        + " Иначе тест case13_NoPickaxe_PickaxeCraftBeforeMine перестанет ловить эту поломку. При смене case13_NoPickaxe_PickaxeCraftBeforeMine проверь это утверждение.");
        assertTrue(
                only(result, "mine_cobble").isEmpty(),
                "Без кирки добыча камня не ставится (нечем копать), сначала кирка: " + result.post()
                        + " Иначе тест case13_NoPickaxe_PickaxeCraftBeforeMine перестанет ловить эту поломку. При смене case13_NoPickaxe_PickaxeCraftBeforeMine проверь это утверждение.");
    }

    @Test
    void case14_NoHoe_HoeCraftBeforeField() {
        GraphWorld world = community(1).built("field_carrot", Built.BUILT).with("dirt", 64);
        world.stage = 2;
        world.tools("spirit-1", -1);
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().matches("graph:craft_(wooden|stone)_hoe@.*")),
                "Тяпки нет — крафт тяпки стоит заданием: " + result.post()
                        + " Иначе тест case14_NoHoe_HoeCraftBeforeField перестанет ловить эту поломку. При смене case14_NoHoe_HoeCraftBeforeField проверь это утверждение.");
        assertTrue(
                only(result, "till_soil").isEmpty(),
                "Без тяпки землю не пашут (нельзя вспахать без инструмента), сначала тяпка: " + result.post()
                        + " Иначе тест case14_NoHoe_HoeCraftBeforeField перестанет ловить эту поломку. При смене case14_NoHoe_HoeCraftBeforeField проверь это утверждение.");
    }

    @Test
    void case15_NoToolMaterial_MaterialFirst() {
        GraphWorld world = community(1)
                .with("oak_log", 3)
                .with("oak_planks", 0)
                .with("stick", 0)
                .with("cobblestone", 0);
        world.tools("spirit-1", -1);
        GraphTickets.Result result = tickets(world);

        assertFalse(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:craft_stone_pickaxe@")),
                "На каменную кирку нет ни палок, ни камня — задание не ставится: " + result.post()
                        + " Иначе тест case15_NoToolMaterial_MaterialFirst перестанет ловить эту поломку. При смене case15_NoToolMaterial_MaterialFirst проверь это утверждение.");
        assertTrue(
                result.post().stream().anyMatch(ticket -> "chop_oak_log".equals(ticket.node())),
                "Сначала материал — дерево для палок и досок: " + result.post()
                        + " Иначе тест case15_NoToolMaterial_MaterialFirst перестанет ловить эту поломку. При смене case15_NoToolMaterial_MaterialFirst проверь это утверждение.");
        assertTrue(
                result.post().stream().anyMatch(ticket -> "craft_oak_planks".equals(ticket.node())),
                "Из брёвен делаются доски — материал для инструмента: " + result.post()
                        + " Иначе тест case15_NoToolMaterial_MaterialFirst перестанет ловить эту поломку. При смене case15_NoToolMaterial_MaterialFirst проверь это утверждение.");
    }

    @Test
    void case16_SharedToolCraft_OneTicketOneSpirit() {
        GraphWorld world = community(3);
        for (int index = 1; index <= 3; index++) {
            world.tools("spirit-" + index, -1);
        }
        world.facts = facts(
                List.of(spirit("spirit-1", List.of()), spirit("spirit-2", List.of()), spirit("spirit-3", List.of())),
                Map.of());
        GraphTickets.Result result = tickets(world);
        List<GraphTickets.Ticket> craft = only(result, "craft_tool:axe");

        assertEquals(
                1,
                craft.size(),
                "Крафт общего инструмента — одна заявка, а не по одной на каждого духа: " + result.post()
                        + " Иначе тест case16_SharedToolCraft_OneTicketOneSpirit перестанет ловить эту поломку. При смене case16_SharedToolCraft_OneTicketOneSpirit проверь это утверждение.");
        assertTrue(
                craft.get(0).task().spirit().isEmpty(),
                "Общая заявка не адресована всем духам сразу: её возьмёт один свободный, а не каждый: " + craft
                        + " Иначе тест case16_SharedToolCraft_OneTicketOneSpirit перестанет ловить эту поломку. При смене case16_SharedToolCraft_OneTicketOneSpirit проверь это утверждение.");
    }

    @Test
    void case23_DifferentTools_DifferentWork() {
        GraphWorld world = community(2).with("oak_log", 0).with("cobblestone", 0);
        world.tools("spirit-1", 1);
        world.tools("spirit-2", -1);
        GraphTickets.Result result = tickets(world);
        GraphTickets.Ticket chop = only(result, "chop_oak_log").get(0);
        BoardTicket clearing = new BoardTicket(
                "building:clearing",
                BoardCase.CLEAR_GROUNDS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.CLEAR_GROUNDS),
                0,
                1,
                "building:clearing",
                List.of(),
                Map.of());
        Board board = new Board(() -> 0);
        board.submit(boardTicket(chop));
        board.submit(clearing);
        DispatchRound round = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> spirits = candidates(world);

        Optional<DispatchRound.Candidate> miner = round.choose(clearing, spirits);

        assertEquals(
                "spirit-1",
                miner.orElseThrow().name(),
                "Расчистка камня — духу с киркой; инструмент предпочтителен"
                        + " Иначе тест case23_DifferentTools_DifferentWork перестанет ловить эту поломку. При смене case23_DifferentTools_DifferentWork проверь это утверждение.");
        assertTrue(
                round.refusal(clearing, spirits.get(1)).isEmpty(),
                "Дух без кирки не запрещён: он может взять работу, но выбирают кирку, когда она есть"
                        + " Иначе тест case23_DifferentTools_DifferentWork перестанет ловить эту поломку. При смене case23_DifferentTools_DifferentWork проверь это утверждение.");
    }

    @Test
    void case25_RequiredTierHeld_NoCraftOfSameTool() {
        GraphWorld world =
                community(1).with("oak_log", 64).with("cobblestone", 64).with("stick", 64);
        GraphTickets.Result result = tickets(world);

        assertFalse(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:craft_stone_axe@")),
                "Каменный топор уже в руках — второй не крафтится: " + result.post()
                        + " Иначе тест case25_RequiredTierHeld_NoCraftOfSameTool перестанет ловить эту поломку. При смене case25_RequiredTierHeld_NoCraftOfSameTool проверь это утверждение.");
        assertFalse(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:craft_stone_pickaxe@")),
                "Каменная кирка уже в руках — вторая не крафтится: " + result.post()
                        + " Иначе тест case25_RequiredTierHeld_NoCraftOfSameTool перестанет ловить эту поломку. При смене case25_RequiredTierHeld_NoCraftOfSameTool проверь это утверждение.");
    }

    @Test
    void case26_WornTool_RepairRaised() {
        GraphWorld world = community(1);
        world.facts = facts(
                List.of(new GraphFacts.Spirit(
                        "spirit-1",
                        new GraphFacts.Position(0, 64, 0),
                        20,
                        Math.max(20, 1f),
                        null,
                        20,
                        300,
                        false,
                        false,
                        false,
                        null,
                        Map.of(),
                        List.of(),
                        List.of(new GraphFacts.Tool("minecraft:stone_axe", "axe", 1, 10, 250)),
                        null)),
                Map.of());
        GraphPlan plan = plan(world);

        assertTrue(
                plan.task("event:tool_worn:repair_tool").isPresent(),
                "Топор изношен — на доске починка или замена, а не молчание: " + plan.tasks()
                        + " Иначе тест case26_WornTool_RepairRaised перестанет ловить эту поломку. При смене case26_WornTool_RepairRaised проверь это утверждение.");
    }
}

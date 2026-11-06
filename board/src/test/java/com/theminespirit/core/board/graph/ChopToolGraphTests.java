package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.candidates;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.only;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolLossRelease;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ChopToolGraphTests {
    @Test
    void case67_EmptyStart_ChopGivenToBareHandsWithoutAxeCraft() {
        GraphWorld world = GraphWorld.empty();
        world.with("oak_log", 0).with("oak_planks", 0).with("stick", 0);
        GraphTickets.Result result = tickets(world);
        List<GraphTickets.Ticket> chops = result.post().stream()
                .filter(ticket -> ticket.node().startsWith("chop_"))
                .toList();

        assertFalse(
                chops.isEmpty(),
                "Пустой старт без топора и брёвен: граф всё равно ставит рубку — дерево рубят руками, топор "
                        + "не ждут (владелец 05.11, случай 67): " + result.post());
        assertTrue(
                result.post().stream()
                        .noneMatch(ticket ->
                                ticket.id().contains("_axe") && ticket.id().contains("craft")),
                "Крафт топора без материала не ставится: сначала рубка руками, потом доски и топор: " + result.post());
        assertTrue(
                dispatch(world, chops).stream().anyMatch(pair -> pair.ticket().startsWith("graph:chop_")),
                "Рубка выдаётся духу с пустыми руками и выполняется руками: " + dispatch(world, chops));
    }

    @Test
    void case68_LogsToPlanksTableAxe_ThenChopWithAxe() {
        GraphWorld world = GraphWorld.empty();
        world.with("oak_log", 8).with("oak_planks", 0).with("stick", 0);
        GraphTickets.Result logs = tickets(world);

        assertTrue(
                logs.post().stream()
                        .anyMatch(ticket -> "oak_planks".equals(ticket.task().makes())),
                "Брёвна есть — граф ставит доски первым шагом к топору (случай 68): " + logs.post());
        assertFalse(
                logs.post().stream()
                        .anyMatch(ticket -> "wooden_axe".equals(ticket.task().makes())),
                "Без досок и верстака топор не крафтится: " + logs.post());

        world.with("oak_planks", 8).with("stick", 4);
        GraphTickets.Result planks = tickets(world);
        assertTrue(
                planks.post().stream().anyMatch(ticket -> "crafting_table".equals(ticket.node())),
                "Доски на складе — граф ставит верстак: " + planks.post());
        assertFalse(
                planks.post().stream()
                        .anyMatch(ticket -> "wooden_axe".equals(ticket.task().makes())),
                "Топор ждёт верстак: без постройки его не крафтят: " + planks.post());

        world.buildings.put("crafting_table", GraphState.Built.BUILT);
        GraphTickets.Result table = tickets(world);
        assertTrue(
                table.post().stream()
                        .anyMatch(ticket -> "wooden_axe".equals(ticket.task().makes())),
                "Верстак построен — граф ставит деревянный топор: " + table.post());

        world.tools("spirit-1", 0);
        GraphTickets.Result armed = tickets(world);
        assertTrue(
                dispatch(world, only(armed, "chop_oak_log")).stream()
                        .anyMatch(pair -> pair.ticket().contains("chop_")),
                "С топором рубка идёт дальше и достаётся духу с топором: " + armed.post());
    }

    @Test
    void case69_AxeInChest_TakeItAndDoNotCraft() {
        GraphWorld world = GraphDispatchChainTests.withoutAxes(1, Map.of("minecraft:stone_axe", 1));
        world.with("oak_log", 0);
        GraphTickets.Result result = tickets(world);
        List<String> ids = result.post().stream().map(GraphTickets.Ticket::id).toList();

        assertTrue(
                ids.stream().anyMatch(id -> id.contains("gear_equip_best")),
                "Топор лежит в сундуке — граф ставит «взять топор» (случай 69): " + ids);
        assertFalse(
                result.post().stream()
                        .anyMatch(ticket ->
                                ticket.id().contains("_axe") && ticket.id().contains("craft")),
                "Топор на складе закрывает нужду — крафт не ставится: " + ids);
        assertTrue(
                dispatch(world, only(result, "chop_oak_log")).stream()
                        .anyMatch(pair -> pair.ticket().contains("chop_")),
                "С топором из сундука рубка выдаётся, а её план берёт топор до дерева: " + result.post());
    }

    @Test
    void case70_AxeBroke_TicketStaysWithSpiritAndSameTree() {
        GraphWorld world = GraphDispatchChainTests.withoutAxes(1, GraphDispatchChainTests.NO_CHEST);
        world.with("oak_log", 0);
        world.kits.get("spirit-1").put("axe", 0);
        Board board = new Board(() -> 0);
        GraphDispatchChainTests.feed(board, only(tickets(world), "chop_oak_log"));
        List<DispatchRound.Candidate> spirits = candidates(world);
        board.claim("graph:chop_oak_log", spirits.get(0).id(), 0);

        List<ToolLossRelease.Released> released =
                ToolLossRelease.apply(board, spirits.get(0).id(), List.of(), false, ticket -> null, List.of());

        assertTrue(
                released.isEmpty(),
                "Топор сломался посреди дерева — задание не возвращается на доску (случай 70): " + released);
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf("graph:chop_oak_log").orElseThrow(),
                "То же дерево дорубается тем же духом: руками или новым топором, но не отдаётся другому");
        assertEquals(
                Optional.of(spirits.get(0).id()),
                board.holderOf("graph:chop_oak_log"),
                "Держатель задания не меняется от поломки топора");
    }

    @Test
    void chopPlan_AxeMayBeInChest_TakesItButNeverBlocksChop() {
        List<ScriptCall> calls = new ArrayList<>();
        collect(
                TicketPlans.plan(
                        BoardCase.GATHER_MATERIALS,
                        TicketTarget.at(new BlockCoord(0, 64, 0)).withSupply("jungle_log", 8)),
                calls);
        int withdraw = first(
                calls,
                call -> "withdraw".equals(call.script())
                        && String.valueOf(call.params().get("item")).endsWith("_axe"));
        int chop = first(calls, call -> "chop_tree".equals(call.script()));
        assertTrue(withdraw >= 0, "Перед рубкой дух пробует взять топор со склада: " + calls);
        assertTrue(chop > withdraw, "Взять топор идёт раньше рубки, а сама рубка не ждёт топор: " + calls);
        assertTrue(
                calls.stream()
                        .noneMatch(call -> "acquire".equals(call.script())
                                && String.valueOf(call.params().get("item")).endsWith("_axe")),
                "Топор не обязателен: план не крафтит его вместо рубки руками, иначе пустой старт ждёт инструмент: "
                        + calls);
    }

    private static int first(List<ScriptCall> calls, Predicate<ScriptCall> match) {
        for (int index = 0; index < calls.size(); index++) {
            if (match.test(calls.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}

package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.ahead;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CrossBattleWorkScenarioPart2Tests {
    private static GraphTickets.Ticket ticket(
            GraphTickets.Result result, Predicate<GraphTickets.Ticket> match, String absent) {
        return result.post().stream()
                .filter(match)
                .findFirst()
                .orElseThrow(() -> new AssertionError(absent + ": " + result.post()));
    }

    @Test
    void combat_MobsAtBuilderAndChopper_RetreatPreemptsWork() {
        GraphWorld world = stageTwo();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 0).with("oak_planks", 0).with("cobblestone", 64).with("dirt", 64);
        GraphFacts calm = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphTickets.Result calmTickets = tickets(world, calm);
        GraphTickets.Ticket build = ticket(
                calmTickets, ticket -> ticket.id().startsWith("graph:building:hut"), "В сцене нет стройки хижины");
        GraphTickets.Ticket chop =
                ticket(calmTickets, ticket -> ticket.node().startsWith("chop_"), "В сцене нет рубки");
        Board board = new Board(() -> 0);
        board.submit(boardTicket(build));
        board.submit(boardTicket(chop));
        board.claim(build.id(), id("spirit-1"), 0);
        board.claim(chop.id(), id("spirit-2"), 0);
        GraphFacts danger = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2),
                        threat("mob-c", "spirit-2", GraphFacts.TargetKind.SPIRIT, 3)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphTickets.Result dangerTickets = tickets(world, danger);
        GraphPlan plan = plan(world, danger);
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphTickets.Ticket retreat = dangerTickets.post().stream()
                .filter(ticket -> ticket.id().endsWith(":retreat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет бегства в графе: " + dangerTickets.post()));
        board.submit(boardTicket(retreat));
        assertEquals(
                List.of(),
                ahead(plan, defend),
                "Раненый дух под мобами получает защиту на потолке:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + ids(plan));
        assertTrue(
                plan.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "Стройка не исчезает из графа при угрозе:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + ids(plan));
        assertTrue(
                plan.task(chop.node()).filter(GraphPlan.Task::feasible).isPresent(),
                "Рубка не исчезает из графа при угрозе:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + ids(plan));
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate builder = new DispatchRound.Candidate(
                id("spirit-1"), "spirit-1", "build_schematic", List.of(), FED, SpiritView.free(), false, List.of());
        DispatchRound.Candidate chopper = new DispatchRound.Candidate(
                id("spirit-2"), "spirit-2", "chop_tree", List.of(), FED, SpiritView.free(), false, List.of());
        assertTrue(
                round.choose(boardTicket(retreat), List.of(builder)).isPresent(),
                "Раненый строитель бросает хижину и уходит от мобов Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение.");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(build.id()).orElseThrow(),
                "Вытесненная стройка возвращается на доску, а не держится за бегущим духом Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение.");
        assertEquals(
                List.of(build.id()),
                round.drainDisplaced(id("spirit-1")).stream()
                        .map(BoardTicket::id)
                        .toList(),
                "Мод получает снятую стройку, чтобы остановить действие духа Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение.");
        assertTrue(
                round.refusal(boardTicket(retreat), builder).isEmpty(),
                "После вытеснения строитель свободен для бегства:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + round.refusal(boardTicket(retreat), builder));
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(chop.id()).orElseThrow(),
                "Рубка второго духа не снята: он не цель бегства Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение.");
        Optional<String> chopperRefusal = round.refusal(boardTicket(retreat), chopper);
        assertTrue(
                chopperRefusal.isPresent(),
                "Рубящий без выбора бегства остаётся занят:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + chopperRefusal);
        assertTrue(
                chopperRefusal.orElseThrow().contains("занят"),
                "Отказ рубящему называет занятость рубкой:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + chopperRefusal);
        assertEquals(
                BoardCase.ACTIVITY_DEFEND,
                BoardActivities.caseOfScript("defend").orElseThrow(),
                "Защита выполняется скриптом defend Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение.");
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Стройка под мобами остаётся схемой build_schematic:  Иначе тест combat_MobsAtBuilderAndChopper_RetreatPreemptsWork перестанет ловить эту поломку. При смене combat_MobsAtBuilderAndChopper_RetreatPreemptsWork проверь это утверждение."
                        + scripts(BoardCase.BUILD_HOUSE));
    }
}

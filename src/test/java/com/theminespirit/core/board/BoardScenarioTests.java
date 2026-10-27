package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardScenarioTests {
    private static final int SEEDS = 150;
    private static final int ROUNDS = 24;

    private static List<BoardScenario.Anomaly> sweep(BoardScenario.ScenarioCheck check) {
        List<BoardScenario.Anomaly> found = new ArrayList<>();
        for (long seed = 1; seed <= SEEDS; seed++) {
            found.addAll(new BoardScenario(seed).run(ROUNDS, check).anomalies);
        }
        return found;
    }

    private static String first(List<BoardScenario.Anomaly> found) {
        return found.size() + " случаев, первые: " + found.subList(0, Math.min(3, found.size()));
    }

    @Test
    void scenarios_ToolCase_NotHeldWithoutTool() {
        List<BoardScenario.Anomaly> found = sweep((scenario, round) -> {
            for (BoardTicket held : scenario.assigned()) {
                Optional<UUID> holder = scenario.board.holderOf(held.id());
                Optional<BoardScenario.Spirit> spirit = holder.flatMap(scenario::spirit);
                if (spirit.isPresent()
                        && !ToolGate.allows(
                                held.boardCase(), spirit.orElseThrow().items())) {
                    scenario.anomalies.add(
                            new BoardScenario.Anomaly("TOOL_HELD_WITHOUT_TOOL", scenario.seed, round, held.id()));
                }
            }
        });
        assertTrue(found.isEmpty(), "Задание с инструментом держит дух без инструмента (INC-94): " + first(found));
    }

    @Test
    void scenarios_DeadHolder_Released() {
        List<BoardScenario.Anomaly> found = sweep((scenario, round) -> {
            for (BoardTicket held : scenario.assigned()) {
                UUID holder = scenario.board.holderOf(held.id()).orElseThrow();
                if (scenario.dead.contains(holder)) {
                    scenario.anomalies.add(new BoardScenario.Anomaly("DEAD_HOLDER", scenario.seed, round, held.id()));
                }
            }
        });
        assertTrue(found.isEmpty(), "Задание держит погибший дух: " + first(found));
    }

    @Test
    void scenarios_Spirit_HoldsAtMostLimit() {
        List<BoardScenario.Anomaly> found =
                sweep((scenario, round) -> scenario.board.assignedCounts().forEach((spirit, count) -> {
                    if (count > BoardTuning.maxActivePerSpirit()) {
                        scenario.anomalies.add(
                                new BoardScenario.Anomaly("OVER_HOLD", scenario.seed, round, spirit + "=" + count));
                    }
                }));
        assertTrue(found.isEmpty(), "Дух держит больше заданий, чем позволяет maxActivePerSpirit: " + first(found));
    }

    @Test
    void scenarios_FreeSpirit_NotIdleWithFittingWork() {
        List<BoardScenario.Anomaly> found = sweep((scenario, round) -> {
            DispatchRound fresh = new DispatchRound(scenario.board, scenario.clock[0]);
            for (BoardTicket ticket : scenario.board.claimable(scenario.stockView(), scenario.clock[0])) {
                for (BoardScenario.Spirit spirit : scenario.live()) {
                    if (fresh.fits(ticket, spirit.candidate())) {
                        scenario.anomalies.add(new BoardScenario.Anomaly(
                                "IDLE_WITH_WORK", scenario.seed, round, spirit.name() + " ↔ " + ticket.id()));
                    }
                }
            }
        });
        assertTrue(found.isEmpty(), "Свободный годный дух стоит при выдаваемом задании: " + first(found));
    }

    @Test
    void scenarios_PendingTicket_NotStuckOnDeadDependency() {
        List<BoardScenario.Anomaly> found = sweep((scenario, round) -> {
            for (BoardTicket ticket : scenario.board.all()) {
                BoardStatus status = scenario.board.statusOf(ticket.id()).orElseThrow();
                if (status != BoardStatus.PENDING && status != BoardStatus.WAITING_RESOURCES) {
                    continue;
                }
                for (String dependency : ticket.dependsOn()) {
                    BoardStatus before = scenario.board.statusOf(dependency).orElse(null);
                    if (before == BoardStatus.FAILED || before == BoardStatus.CANCELLED || before == null) {
                        scenario.anomalies.add(new BoardScenario.Anomaly(
                                "DEAD_DEPENDENCY",
                                scenario.seed,
                                round,
                                ticket.id() + " ← " + dependency + " " + before));
                    }
                }
            }
        });
        assertTrue(found.isEmpty(), "Задание навсегда ждёт проваленного или снятого предшественника: " + first(found));
    }

    @Test
    void priorities_ToolCraft_NotBelowCaseNeedingTool() {
        List<String> inverted = new ArrayList<>();
        for (BoardCase user : BoardCase.values()) {
            Optional<String> tool = ToolGate.required(user);
            if (tool.isEmpty()) {
                continue;
            }
            for (BoardCase craft : ToolSet.chain()) {
                if (ToolSet.itemId(craft).endsWith(tool.orElseThrow())
                        && BoardRules.priorityOf(craft) < BoardRules.priorityOf(user)) {
                    inverted.add(craft + "=" + BoardRules.priorityOf(craft) + " < " + user + "="
                            + BoardRules.priorityOf(user));
                }
            }
        }
        assertEquals(
                List.of(),
                inverted,
                "Изготовление инструмента обязано перевешивать задание, которому он нужен: иначе дух копает руками");
    }

    @Test
    void submit_ActivityCase_NotAcceptedAsTicket() {
        Board board = new Board(() -> 0);
        BoardOutcome outcome = board.submit(new BoardTicket(
                "activity:mine",
                BoardCase.ACTIVITY_MINE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.ACTIVITY_MINE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));
        assertTrue(
                outcome != BoardOutcome.ACCEPTED,
                "Занятие (ACTIVITY_*) не выдаётся через claimable: принятое заданием, оно висит вечно");
    }
}

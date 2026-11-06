package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class N24DemolishLiveTests {
    private static final Path TICKET = Path.of("src/test/resources/live/n24-demolish-clear/task-demolish.json");
    private static final String CLEAR_AREA = "clear_area";
    private static final String WAIT = "wait";
    private static final String REFUSAL =
            "ящик (-169,71,-488)..(-169,71,-488) не расчищен: убрано 0 из 1; недостижимо: 1; не убран ни один блок";
    private static final BlockCoord LIVE_A = new BlockCoord(-169, 71, -488);
    private static final BlockCoord LIVE_B = new BlockCoord(-170, 72, -489);
    private static final BlockCoord LIVE_C = new BlockCoord(-169, 75, -488);
    private static final BlockCoord LIVE_D = new BlockCoord(-170, 76, -489);
    private static final List<BlockCoord> HANGING = List.of(LIVE_A, LIVE_B, LIVE_C, LIVE_D);
    private static final int MAX_TICKS = 100;

    @Test
    void liveDemolishTicket_ClearCannotRemoveBlocks_TaskFailsInsteadOfDone() throws IOException {
        JsonNode ticket = new ObjectMapper().readTree(Files.readString(TICKET, StandardCharsets.UTF_8));
        assertEquals(
                "DISMANTLE_SHELTER",
                ticket.get("case").asText(),
                "Живой тикет обязан быть разбором хижины: иначе тест проверяет не тот путь");
        assertEquals(
                BoardTicket.ALWAYS_CAUSE,
                ticket.get("cause").asText(),
                "Живой тикет закрывался по причине always, не сверяясь с миром");
        TaskNode plan = TicketPlans.plan(BoardCase.DISMANTLE_SHELTER, target());
        TaskRunner runner = TaskRunner.start(plan, registry(), ConditionRegistry.of(Map.of()));

        RunStatus status = runToEnd(runner);

        assertEquals(
                RunStatus.FAILED,
                status,
                "Дух отработал план разбора, clear_area ответил «убрано 0 из 1 … не убран ни один блок», "
                        + "а задание закрылось DONE: блоки остаются висеть навсегда. Провал расчистки обязан "
                        + "проваливать разбор, иначе доска врёт о факте мира");
        assertTrue(
                runner.failureReason().orElse("").contains("недостижимо"),
                "Причина провала обязана называть недостижимость из clear_area, а не теряться в fallback: "
                        + runner.failureReason().orElse("причина не записана"));
    }

    @Test
    void liveHangingBlocks_StillInWorld_FailTheTaskWithCoordinates() {
        Optional<String> partial = DemolishAudit.stillStanding(HANGING.size(), HANGING.subList(0, 2));

        assertTrue(
                partial.isPresent(), "Два висящих блока из четырёх ещё стоят в мире: разбор нельзя закрывать успехом");
        assertTrue(
                partial.orElse("").contains("2 из 4"),
                "Причина обязана называть, сколько блоков не убрано: " + partial.orElse("причины нет"));
        assertTrue(
                partial.orElse("").contains(LIVE_A.toString()),
                "Причина обязана называть координаты оставшихся блоков, иначе игрок не найдёт их: "
                        + partial.orElse("причины нет"));
        assertEquals(
                Optional.empty(),
                DemolishAudit.stillStanding(HANGING.size(), List.of()),
                "Когда в мире не осталось ни одного блока плана, разбор закрывается успехом");
    }

    private static TicketTarget target() {
        StageSite site = TownFixtures.flatSite();
        List<PlannedBlock> blocks =
                HANGING.stream().map(at -> new PlannedBlock(at, PlanSteps.AIR)).toList();
        return TicketTarget.at(HANGING.get(0)).withWork(new StageWork(site, blocks, List.of()));
    }

    private static ScriptRegistry registry() {
        ScriptFactory clear = params -> failing(REFUSAL);
        ScriptFactory wait = params -> succeeding();
        return ScriptRegistry.of(Map.of(CLEAR_AREA, clear, WAIT, wait));
    }

    private static RunStatus runToEnd(TaskRunner runner) {
        RunStatus status = runner.tick();
        for (int tick = 0; status == RunStatus.RUNNING && tick < MAX_TICKS; tick++) {
            status = runner.tick();
        }
        return status;
    }

    private static ScriptInstance failing(String reason) {
        return new ScriptInstance() {
            @Override
            public ScriptStatus tick() {
                return ScriptStatus.FAILED;
            }

            @Override
            public String failureReason() {
                return reason;
            }
        };
    }

    private static ScriptInstance succeeding() {
        return () -> ScriptStatus.SUCCEEDED;
    }
}

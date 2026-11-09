package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.chest;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.movement.GateCrossing;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class CrossBattleWorkScenarioTests {
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

        assertEquals(100, defend.priority(), "Раненый дух под мобами получает защиту на потолке: " + ids(plan));
        assertTrue(
                plan.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "Стройка не исчезает из графа при угрозе: " + ids(plan));
        assertTrue(
                plan.task(chop.node()).filter(GraphPlan.Task::feasible).isPresent(),
                "Рубка не исчезает из графа при угрозе: " + ids(plan));
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate builder = new DispatchRound.Candidate(
                id("spirit-1"), "spirit-1", "build_schematic", List.of(), FED, SpiritView.free(), false, List.of());
        DispatchRound.Candidate chopper = new DispatchRound.Candidate(
                id("spirit-2"), "spirit-2", "chop_tree", List.of(), FED, SpiritView.free(), false, List.of());

        assertTrue(
                round.choose(boardTicket(retreat), List.of(builder)).isPresent(),
                "Раненый строитель бросает хижину и уходит от мобов");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(build.id()).orElseThrow(),
                "Вытесненная стройка возвращается на доску, а не держится за бегущим духом");
        assertEquals(
                List.of(build.id()),
                round.drainDisplaced(id("spirit-1")).stream()
                        .map(BoardTicket::id)
                        .toList(),
                "Мод получает снятую стройку, чтобы остановить действие духа");
        assertTrue(
                round.refusal(boardTicket(retreat), builder).isEmpty(),
                "После вытеснения строитель свободен для бегства: " + round.refusal(boardTicket(retreat), builder));
        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(chop.id()).orElseThrow(),
                "Рубка второго духа не снята: он не цель бегства");
        Optional<String> chopperRefusal = round.refusal(boardTicket(retreat), chopper);
        assertTrue(chopperRefusal.isPresent(), "Рубящий без выбора бегства остаётся занят: " + chopperRefusal);
        assertTrue(
                chopperRefusal.orElseThrow().contains("занят"),
                "Отказ рубящему называет занятость рубкой: " + chopperRefusal);
        assertEquals(
                BoardCase.ACTIVITY_DEFEND,
                BoardActivities.caseOfScript("defend").orElseThrow(),
                "Защита выполняется скриптом defend");
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Стройка под мобами остаётся схемой build_schematic: " + scripts(BoardCase.BUILD_HOUSE));
    }

    @Test
    void combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend() {
        GraphWorld world = stageOne();
        world.with("dirt", 64).with("cobblestone", 64).with("oak_log", 64).with("oak_planks", 64);
        GraphFacts facts = facts(
                named(
                        spirit("spirit-1", 4, 20),
                        spirit("spirit-2"),
                        spirit("spirit-3", 20, 20, true, true, "pit", List.of(), List.of())),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task rescue = task(plan, "rescue_spirit");
        GraphPlan.Task retreat = task(plan, "event:life_threat:retreat");

        assertEquals(100, defend.priority(), "Мобы у раненого духа поднимают защиту на потолок: " + ids(plan));
        assertTrue(rescue.feasible(), "Застрявший в яме дух получает спасение: " + ids(plan));
        assertTrue(retreat.feasible(), "Бегство из-под мобов тоже на доске: " + ids(plan));
        assertTrue(defend.priority() > rescue.priority(), "Сначала бой, потом спасение из ямы: " + ids(plan));
        assertEquals(
                RescuePlans.Self.PIT,
                RescuePlans.self(new RescuePlans.Situation(true, true, false, false, true)),
                "Из ямы есть выход по ступеням: механика выбирает PIT, а не прогулку");
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(true, false, false, true, true)),
                "Без ступеней и инструмента из ямы не выйти: механика честно говорит NONE");
        assertEquals(
                BoardCase.ACTIVITY_ESCAPE,
                BoardActivities.caseOfScript("dig_out").orElseThrow(),
                "Выход из клетки — действие escape");
    }

    @Test
    void combat_HungryWoundedSpirit_FightsBeforeEating() {
        GraphWorld world = stageTwo();
        world.with("food", 8).with("coal", 0);
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 4), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(chest(Map.of("food", 8))),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task eat = task(plan, "event:life_threat:eat");
        GraphPlan.Task mine = task(plan, "mine_coal");

        assertEquals(
                100, defend.priority(), "Раненый голодный дух под мобами получает защиту на потолке: " + ids(plan));
        assertTrue(eat.feasible(), "Еда на складе делает обед выполнимым: " + ids(plan));
        assertTrue(defend.priority() > eat.priority(), "Сначала отбиться, потом есть: " + ids(plan));
        assertTrue(
                mine.priority() > eat.priority(),
                "Шахта всё ещё выше еды: голодный дух копает раньше обеда — известный баг 12-14: " + ids(plan));
        assertEquals(
                BoardCase.EAT_TO_FULL, eat.boardCase(), "Голод закрывается внешним кейсом EAT_TO_FULL, а не шахтой");
        assertEquals(
                BoardCase.ACTIVITY_COMBAT,
                BoardActivities.caseOfScript("attack").orElseThrow(),
                "Бой идёт действием attack");
    }

    @Test
    void combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse() {
        GraphWorld world = stageTwo();
        world.built("warehouse", Built.ABSENT).built("fence", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64).with("oak_fence", 5);
        world.event("expedition_ready").event("more4_player_near");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task repair = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task guard = task(plan, "event:more4_player_near:more4_guard_settlement");

        assertEquals(100, defend.priority(), "Набег поднимает защиту на потолок: " + ids(plan));
        assertTrue(expedition.feasible(), "Готовый поход не отменяется набегом: " + ids(plan));
        assertTrue(repair.feasible(), "Побитый забор остаётся выполнимым ремонтом: " + ids(plan));
        assertTrue(defend.priority() > expedition.priority(), "Поход ждёт, пока отобьёмся: " + ids(plan));
        assertTrue(expedition.priority() > repair.priority(), "Поход выше ремонта забора: " + ids(plan));
        assertTrue(repair.priority() > guard.priority(), "Ремонт забора выше патруля: " + ids(plan));
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Склада нет в плане — граф просит стратега, а не строит вслепую: " + plan.notices());
        assertTrue(
                plan.task("building:warehouse").isEmpty(),
                "Без лота склада задание не ставится: " + plan.task("building:warehouse"));
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход выполняется действием follow/escort");

        GateCrossing gate = new GateCrossing(new BlockCoord(5, 64, 0), new BlockCoord(0, 64, 0));
        assertEquals(
                GateCrossing.Phase.INSIDE,
                gate.observe(new BlockCoord(5, 64, 0)),
                "Дух в створке ворот — проход ещё не закончен");
        assertFalse(gate.closable(), "Пока дух в воротах, закрывать нельзя");
        assertEquals(
                GateCrossing.Phase.BEYOND,
                gate.observe(new BlockCoord(6, 64, 0)),
                "Дух вышел на другую сторону — ворота можно закрыть");
        assertTrue(gate.closable(), "После перехода ворота закрываются за духом");
    }

    @Test
    void rescue_TrappedSpiritAndExpedition_RescueOutranksFollow() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64);
        world.event("expedition_ready");
        GraphFacts facts = facts(
                named(
                        spirit("spirit-1"),
                        spirit("spirit-2"),
                        spirit("spirit-3", 20, 20, true, true, "pit", List.of(), List.of())),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task rescue = task(plan, "rescue_spirit");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task hut = task(plan, "building:hut");

        assertTrue(rescue.feasible(), "Застрявший дух получает спасение: " + ids(plan));
        assertTrue(rescue.priority() > expedition.priority(), "Спасение жизни выше похода: " + ids(plan));
        assertTrue(hut.feasible(), "Стройка хижины остаётся на доске рядом со спасением: " + ids(plan));
        assertEquals(
                RescuePlans.Mode.HELPER,
                RescuePlans.assign(id("spirit-3"), 5, "яма: выхода нет", Optional.of(id("spirit-1")))
                        .mode(),
                "Сам дух выйти не может — доска назначает помощника");
        assertTrue(
                scripts(BoardCase.RESCUE_SPIRIT).contains("clear_area"),
                "Спасатель разбирает клетку скриптом clear_area: " + scripts(BoardCase.RESCUE_SPIRIT));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Хижина после спасения строится схемой build_schematic: " + scripts(BoardCase.BUILD_HOUSE));
    }
}

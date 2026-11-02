package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageRollbackTests {
    private static final Map<String, Integer> BUILDING_STAGES =
            Map.of("mine", 1, "hut", 1, "plaza", 2, "warehouse", 2, "mine_level_2", 2);

    private static StageRules rules() {
        return new StageRules(List.of(
                new StageRules.Stage(1, Map.of(BoardCase.BUILD_MINE, 1, BoardCase.BUILD_HOUSE, 1)),
                new StageRules.Stage(
                        2,
                        Map.of(BoardCase.BUILD_MINE, 2, BoardCase.BUILD_WAREHOUSE, 2),
                        List.of("plaza"),
                        Map.of("plaza", 1))));
    }

    private static BoardLedger firstStageBuilt() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(BoardCase.BUILD_MINE, 1);
        ledger.complete(BoardCase.BUILD_HOUSE, 1);
        return ledger;
    }

    private static StageRollback.World world(Map<String, StageRollback.Readiness> states) {
        return (boardCase, planId, level) -> states.getOrDefault(
                boardCase == null ? planId + ":" + level : boardCase.name() + ":" + level,
                StageRollback.Readiness.UNKNOWN);
    }

    private static List<String> keys(StageRollback.Plan plan) {
        List<String> keys = new ArrayList<>();
        plan.forget().forEach(mark -> keys.add(mark.key()));
        return keys;
    }

    @Test
    void view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing() {
        BoardLedger ledger = firstStageBuilt();
        StageRollback.World world = world(Map.of(
                "BUILD_MINE:1", StageRollback.Readiness.NOT_READY,
                "BUILD_HOUSE:1", StageRollback.Readiness.READY));

        StageView view = StageRollback.view(rules(), BUILDING_STAGES, ledger, world);

        assertEquals(2, view.stage(), "Этап считается по закрытым отметкам: все постройки первого этапа отмечены");
        assertTrue(
                view.requirements().stream()
                        .anyMatch(requirement -> requirement.stage() == 1
                                && "BUILD_MINE".equals(requirement.id())
                                && requirement.closed()
                                && requirement.world() == StageRollback.Readiness.NOT_READY),
                "GET обязан показать шахту первого этапа закрытой журналом, но не готовой по миру: "
                        + "иначе владелец не увидит, почему этап открылся при 96% шахты. Требования: "
                        + view.requirements());
        assertTrue(
                view.missing().containsKey("BUILD_MINE"),
                "Остаток второго этапа обязан включать шахту уровня 2: без неё этап не закрыт. Остаток: "
                        + view.missing());
        assertEquals(
                3,
                view.requirements().stream()
                        .filter(requirement -> requirement.stage() == 2)
                        .count(),
                "GET обязан перечислить все постройки второго этапа: две постройки доски и площадь плана — "
                        + "по списку видно, что именно открыто. Требования: " + view.requirements());
    }

    @Test
    void plan_RollbackToFirst_ForgetsHigherMarksAndWorldDrift() {
        BoardLedger ledger = firstStageBuilt();
        ledger.complete(BoardCase.BUILD_WAREHOUSE, 2);
        ledger.notePlan("plaza", 1);
        StageRollback.World world = world(Map.of(
                "BUILD_MINE:1", StageRollback.Readiness.NOT_READY,
                "BUILD_HOUSE:1", StageRollback.Readiness.READY,
                "BUILD_WAREHOUSE:2", StageRollback.Readiness.READY,
                "plaza:1", StageRollback.Readiness.READY));

        StageRollback.Plan plan = StageRollback.plan(1, rules(), BUILDING_STAGES, ledger, world);

        assertNull(
                plan.refusal(),
                "Откат 2→1 обязан приниматься: шахта первого этапа не готова по миру. " + "Отказ: " + plan.refusal());
        assertEquals(2, plan.previous(), "Откат обязан назвать прежний этап 2: карточка пишет «откат 2→1»");
        assertEquals(
                Set.of("building:BUILD_MINE:1", "building:BUILD_WAREHOUSE:2", "building:plaza:1"),
                Set.copyOf(keys(plan)),
                "Откат на первый этап обязан снять отметки второго этапа и неготовую по миру шахту первого, "
                        + "но оставить готовый дом: мир не ломаем, готовое не перестраиваем. Снятые: " + keys(plan));
    }

    @Test
    void plan_FirstStageReady_RefusesWithoutForgetting() {
        BoardLedger ledger = firstStageBuilt();
        ledger.complete(BoardCase.BUILD_MINE, 2);
        StageRollback.World world = world(Map.of(
                "BUILD_MINE:1", StageRollback.Readiness.READY,
                "BUILD_HOUSE:1", StageRollback.Readiness.READY));

        StageRollback.Plan plan = StageRollback.plan(1, rules(), BUILDING_STAGES, ledger, world);

        assertTrue(
                plan.refused(),
                "Откат обязан отказать, когда весь первый этап готов по миру: иначе журнал разойдётся с миром. "
                        + "Снятые: " + keys(plan));
        assertEquals(
                List.of(),
                plan.forget(),
                "Отказ не имеет права трогать отметки: мир остаётся целым, повтор с исправленным миром не нужен");
    }

    @Test
    void plan_ForwardStage_Refuses() {
        BoardLedger ledger = new BoardLedger();

        StageRollback.Plan plan = StageRollback.plan(2, rules(), BUILDING_STAGES, ledger, world(Map.of()));

        assertTrue(
                plan.refused(), "Этап вперёд не ставится: он закрывается постройкой целиком, а не запросом владельца");
        assertEquals(1, plan.previous(), "Отказ обязан показать, где поселение стоит сейчас");
        assertEquals(List.of(), plan.forget(), "Отказ вперёд не меняет отметки");
    }

    @Test
    void plan_SameStage_ChangesNothing() {
        BoardLedger ledger = new BoardLedger();

        StageRollback.Plan plan = StageRollback.plan(1, rules(), BUILDING_STAGES, ledger, world(Map.of()));

        assertNull(plan.refusal(), "Повтор отката на текущий этап — успешный идемпотентный вызов");
        assertEquals(List.of(), plan.forget(), "Повтор отката на текущий этап не снимает ни одной отметки");
    }

    @Test
    void plan_TargetOutsideStages_Throws() {
        BoardLedger ledger = new BoardLedger();

        assertThrows(
                IllegalArgumentException.class,
                () -> StageRollback.plan(3, rules(), BUILDING_STAGES, ledger, world(Map.of())),
                "Этап вне списка этапов обязан отвергаться: иначе карточка напишет несуществующий этап");
        assertThrows(
                IllegalArgumentException.class,
                () -> StageRollback.plan(0, rules(), BUILDING_STAGES, ledger, world(Map.of())),
                "Нулевой этап обязан отвергаться: этапы поселения начинаются с первого");
    }

    @Test
    void plan_UnknownMarks_AreKept() {
        BoardLedger ledger = firstStageBuilt();
        ledger.complete(BoardCase.CRAFT_FURNACE, 1);
        ledger.notePlan("mystery", 1);
        StageRollback.World world = world(Map.of("BUILD_MINE:1", StageRollback.Readiness.NOT_READY));

        StageRollback.Plan plan = StageRollback.plan(1, rules(), BUILDING_STAGES, ledger, world);

        assertTrue(
                !keys(plan).contains("building:CRAFT_FURNACE:1"),
                "Отметка кейса вне этапов обязана пережить откат: печь не принадлежит ни одному этапу, и её снятие "
                        + "заставило бы духа строить заново уже стоящее. Снятые: " + keys(plan));
        assertTrue(
                !keys(plan).contains("building:mystery:1"),
                "Неизвестная отметка плана обязана пережить откат: откат не сносит то, чего не понимает. Снятые: "
                        + keys(plan));
    }

    @Test
    void ticketStage_PlanIdOfGraph_MapsToItsStage() {
        StageRules rules = rules();

        assertEquals(
                2,
                stage("graph:building:plaza", BoardCase.BUILD_PLACED, rules),
                "Задание площади обязано относиться ко второму этапу: иначе откат 2→1 оставит его на доске");
        assertEquals(
                2,
                stage("graph:building:mine_level_2", BoardCase.BUILD_MINE, rules),
                "Задание шахты уровня 2 обязано относиться ко второму этапу: этап берётся из данных постройки");
        assertEquals(
                1,
                stage("first:build_mine", BoardCase.BUILD_MINE, rules),
                "Задание шахты первого этапа обязано остаться при откате на первый этап");
    }

    @Test
    void ticketStage_StageOfPlanAlias_IsKnown() {
        assertEquals(
                2,
                stage("graph:building:storage:2", BoardCase.BUILD_WAREHOUSE, rules()),
                "Задание склада с частью «:2» обязано относиться ко второму этапу по псевдониму storage→warehouse: "
                        + "иначе часть задания переживёт откат и доска будет выдавать её духам");
    }

    @Test
    void ticketStage_UnstagedTicket_IsUnknown() {
        OptionalInt stage = StageRollback.ticketStage(
                "repair:build_house:1", BoardCase.REPAIR_BUILDING, 1, rules(), BUILDING_STAGES);

        assertTrue(
                stage.isEmpty(),
                "Ремонт вне этапов не привязан к этапу: откат не имеет права снимать общепоселковый ремонт");
    }

    private static int stage(String ticketId, BoardCase boardCase, StageRules rules) {
        return StageRollback.ticketStage(ticketId, boardCase, 1, rules, BUILDING_STAGES)
                .orElseThrow(() -> new IllegalStateException("Задание '" + ticketId + "' осталось без этапа"));
    }
}

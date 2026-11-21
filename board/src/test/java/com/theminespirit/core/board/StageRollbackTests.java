package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
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

    @Test
    void view_ConfirmedGateSnapshot_ReportsLiveWorldMissing() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(BoardCase.BUILD_MINE, 1);
        ledger.complete(BoardCase.BUILD_HOUSE, 1);
        ledger.gate(new StageGate(1, Map.of("BUILD_HOUSE", 1, "BUILD_MINE", 1)));
        StageRollback.World world = world(Map.of(
                "BUILD_HOUSE:1", StageRollback.Readiness.READY,
                "BUILD_MINE:1", StageRollback.Readiness.READY));
        StageView view = StageRollback.view(rules(), BUILDING_STAGES, ledger, world);
        assertNull(
                view.missing().get("BUILD_HOUSE"),
                "Дом закрыт журналом и готов по миру — его нет в нехватке: снимок гейта не выдаётся за "
                        + "текущий аудит (observe25 B13). Нехватка:  Иначе тест view_ConfirmedGateSnapshot_ReportsLiveWorldMissing перестанет ловить эту поломку. При смене view_ConfirmedGateSnapshot_ReportsLiveWorldMissing проверь это утверждение."
                        + view.missing());
        assertNull(
                view.missing().get("BUILD_MINE"),
                "Шахта закрыта и готова по миру — её тоже нет в нехватке:  Иначе тест view_ConfirmedGateSnapshot_ReportsLiveWorldMissing перестанет ловить эту поломку. При смене view_ConfirmedGateSnapshot_ReportsLiveWorldMissing проверь это утверждение."
                        + view.missing());
    }

    @Test
    void view_ClosedButWorldNotReady_StaysInMissing() {
        BoardLedger ledger = new BoardLedger();
        ledger.complete(BoardCase.BUILD_MINE, 1);
        ledger.complete(BoardCase.BUILD_HOUSE, 1);
        ledger.gate(new StageGate(1, Map.of()));
        StageRollback.World world = world(Map.of(
                "BUILD_HOUSE:1", StageRollback.Readiness.READY,
                "BUILD_MINE:1", StageRollback.Readiness.NOT_READY));
        StageView view = StageRollback.view(rules(), BUILDING_STAGES, ledger, world);
        assertEquals(
                1,
                view.missing().get("BUILD_MINE"),
                "Журнал закрыл шахту, но мир не готов — это текущая нехватка, а не закрытая строка:  Иначе тест view_ClosedButWorldNotReady_StaysInMissing перестанет ловить эту поломку. При смене view_ClosedButWorldNotReady_StaysInMissing проверь это утверждение."
                        + view.missing());
    }

    @Test
    void view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing() {
        BoardLedger ledger = firstStageBuilt();
        StageRollback.World world = world(Map.of(
                "BUILD_MINE:1", StageRollback.Readiness.NOT_READY,
                "BUILD_HOUSE:1", StageRollback.Readiness.READY));
        StageView view = StageRollback.view(rules(), BUILDING_STAGES, ledger, world);
        assertEquals(
                2,
                view.stage(),
                "Этап считается по закрытым отметкам: все постройки первого этапа отмечены Иначе тест view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing перестанет ловить эту поломку. При смене view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing проверь это утверждение.");
        assertTrue(
                view.requirements().stream()
                        .anyMatch(requirement -> requirement.stage() == 1
                                && "BUILD_MINE".equals(requirement.id())
                                && requirement.closed()
                                && requirement.world() == StageRollback.Readiness.NOT_READY),
                "GET обязан показать шахту первого этапа закрытой журналом, но не готовой по миру: "
                        + "иначе владелец не увидит, почему этап открылся при 96% шахты. Требования:  При смене view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing проверь это утверждение."
                        + view.requirements());
        assertTrue(
                view.missing().containsKey("BUILD_MINE"),
                "Остаток второго этапа обязан включать шахту уровня 2: без неё этап не закрыт. Остаток:  Иначе тест view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing перестанет ловить эту поломку. При смене view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing проверь это утверждение."
                        + view.missing());
        assertEquals(
                3,
                view.requirements().stream()
                        .filter(requirement -> requirement.stage() == 2)
                        .count(),
                "GET обязан перечислить все постройки второго этапа: две постройки доски и площадь плана — "
                        + "по списку видно, что именно открыто. Требования:  Иначе тест view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing перестанет ловить эту поломку. При смене view_StageTwo_ListsEarlierStageDriftAndCurrentStageMissing проверь это утверждение."
                        + view.requirements());
    }
}

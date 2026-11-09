package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageRollbackGateTests {
    private static final Map<String, Integer> BUILDING_STAGES =
            Map.of("mine", 1, "hut", 1, "plaza", 2, "warehouse", 2, "mine_level_2", 2);

    @Test
    void plan_JournalAheadOrBehindConfirmedGate_UsesGateStage() {
        StageRules rules = rules();
        BoardLedger ahead = firstStageBuilt();
        ahead.complete(BoardCase.BUILD_MINE, 2);
        ahead.complete(BoardCase.BUILD_WAREHOUSE, 2);
        ahead.notePlan("plaza", 1);
        ahead.gate(new StageGate(1, Map.of("BUILD_HOUSE", 1, "BUILD_MINE", 1)));
        StageRollback.World ready = world(Map.of());

        StageRollback.Plan forward = StageRollback.plan(2, rules, BUILDING_STAGES, ahead, ready);

        assertTrue(
                forward.refused(),
                "Журнал впереди гейта мира: фактический этап 1, поэтому этап 2 вперёд не ставится, а не «уже стоит»; "
                        + "иначе ручной откат разойдётся с миром. Отказ: " + forward.refusal());
        assertEquals(1, forward.previous(), "Прежний этап берётся из подтверждённого гейта мира, а не из журнала");
        assertTrue(
                forward.refusal().contains("BUILD_HOUSE"),
                "Отказ называет остаток подтверждённого гейта, а не пустой журнал: " + forward.refusal());

        BoardLedger behind = firstStageBuilt();
        behind.gate(new StageGate(2, Map.of("BUILD_MINE", 2)));

        StageRollback.Plan same = StageRollback.plan(2, rules, BUILDING_STAGES, behind, ready);

        assertNull(
                same.refusal(),
                "Журнал отстаёт от гейта мира: фактический этап уже 2, откат на него — идемпотентный вызов, а не "
                        + "шаг вперёд. Отказ: " + same.refusal());
        assertEquals(2, same.previous(), "Текущий этап поселения — подтверждённый миром, а не журнальный");
    }

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
}

package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageClearingGateTests {
    @Test
    void stages_EveryStageWithoutClearing_KeepsStepAndRollsBack() {
        StageRules rules = StageRules.load();
        rules.stages()
                .forEach(stage -> assertTrue(
                        stage.buildings().containsKey(BoardCase.CLEAR_GROUNDS),
                        "Этап " + stage.stage()
                                + " не требует ровной зоны: ямы после перехода остались бы без работы расчистки"));

        StageWorld defect = new StageWorld() {
            @Override
            public boolean built(BoardCase boardCase, int level) {
                return boardCase != BoardCase.CLEAR_GROUNDS;
            }

            @Override
            public boolean builtPlan(String id, int level) {
                return true;
            }
        };

        assertEquals(1, SettlementStage.of(defect), "Дефект зоны снова открывает первый этап, где живёт расчистка");
        assertEquals(1, new StageConfirmation(5, 1).gate(defect).stage(), "Гейт возвращает этап к состоянию мира");
    }
}

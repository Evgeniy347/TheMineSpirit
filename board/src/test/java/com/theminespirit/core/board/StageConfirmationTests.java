package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageConfirmationTests {
    @Test
    void observe_SingleBadAudit_KeepsConfirmedStage() {
        StageConfirmation confirmation = new StageConfirmation(2);

        assertEquals(2, confirmation.observe(1), "Один сбой аудита не откатывает этап: мир мог не загрузиться");
        assertEquals(2, confirmation.stage(), "Подтверждённый этап остаётся прежним");
        assertEquals(1, confirmation.candidate(), "Сбой запомнен как кандидат, а не потерян");
    }

    @Test
    void observe_ThreeBadAudits_RollsStageBack() {
        StageConfirmation confirmation = new StageConfirmation(2);

        assertEquals(2, confirmation.observe(1), "Первый плохой аудит этап не откатывает");
        assertEquals(2, confirmation.observe(1), "Второй плохой аудит этап не откатывает");
        assertEquals(1, confirmation.observe(1), "Третий одинаковый аудит — граница подтверждения отката");
        assertEquals(1, confirmation.stage(), "Этап откатился на подтверждённый мир");
        assertEquals(1, confirmation.observe(1), "Откат держится, пока мир не восстановлен");
    }

    @Test
    void observe_GoodAuditBetweenBad_ResetsStreak() {
        StageConfirmation confirmation = new StageConfirmation(2);

        confirmation.observe(1);
        confirmation.observe(1);
        confirmation.observe(2);
        confirmation.observe(1);
        confirmation.observe(1);

        assertEquals(2, confirmation.stage(), "Сбой аудита после хорошего начинает счёт заново");
        assertEquals(2, confirmation.streak(), "Два сбоя подряд ещё не смена этапа");
    }

    @Test
    void observe_HigherStage_WaitsForConfirmations() {
        StageConfirmation confirmation = new StageConfirmation(1);

        assertEquals(1, confirmation.observe(2), "Первый аудит не открывает этап: нужен ряд подтверждений");
        assertEquals(1, confirmation.observe(2), "Второй аудит ещё не открывает этап");
        assertEquals(2, confirmation.observe(2), "Третий аудит подряд открывает этап");
        assertEquals(2, confirmation.stage(), "Этап растёт только по подтверждённому аудиту мира");
    }

    @Test
    void gate_WorldMovesStage_ReturnsGateOfConfirmedStage() {
        StageRules rules = StageRules.load();
        Set<String> first = new HashSet<>();
        rules.stages().get(0).buildings().forEach((boardCase, level) -> first.add(boardCase.name() + ":" + level));
        StageWorld built = new SetWorld(first);
        StageConfirmation confirmation = new StageConfirmation(1);

        assertEquals(1, confirmation.gate(built).stage(), "Первый аудит держит прежний этап");
        confirmation.gate(built);
        StageGate gate = confirmation.gate(built);

        assertEquals(2, gate.stage(), "Подтверждённый аудит открывает второй этап");
        assertEquals(
                SettlementStage.missing(built, 2),
                gate.missing(),
                "Гейт подтверждённого этапа называет остаток второго этапа по тому же миру");
    }

    @Test
    void constructor_WithoutConfirmations_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StageConfirmation(1, 0),
                "Нулевое подтверждение означало бы смену этапа на каждом тике — запрещено");
    }

    private record SetWorld(Set<String> built) implements StageWorld {
        @Override
        public boolean built(BoardCase boardCase, int level) {
            return built.contains(boardCase.name() + ":" + level);
        }

        @Override
        public boolean builtPlan(String id, int level) {
            return built.contains(id + ":" + level);
        }
    }
}

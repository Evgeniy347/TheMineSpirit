package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChestShareTests {

    @Test
    void takes_StacksAboveKeep_SurplusTakenNearestFirst() {
        assertEquals(
                List.of(64, 36, 0),
                ChestShare.takes(List.of(64, 128, 32), 64, 100),
                "Земля сверх минимума 64 из 224 в сундуках берётся с ближнего: духи не копают за оградой при "
                        + "стаках в сундуках (INC-73)");
    }

    @Test
    void takes_OnlyKeepInChests_NothingTaken() {
        assertEquals(
                List.of(0, 0),
                ChestShare.takes(List.of(40, 24), 64, 50),
                "Запас сундуков не выше минимума 64 — не трогается: минимум поселения остаётся");
    }

    @Test
    void takes_SurplusSmallerThanNeed_OnlySurplus() {
        assertEquals(
                List.of(16),
                ChestShare.takes(List.of(80), 64, 50),
                "Из 80 при минимуме 64 берётся 16, остальное — добычей: минимум не трогается");
    }
}

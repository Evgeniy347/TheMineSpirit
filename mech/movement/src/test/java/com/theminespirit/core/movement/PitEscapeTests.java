package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PitEscapeTests {

    @Test
    void exit_FlatGround_NotTrapped() {
        assertEquals(
                Optional.empty(),
                PitEscape.exit((dx, dz) -> 70),
                "На ровном дух не в яме: выход не нужен, задание не снимается");
    }

    @Test
    void exit_PitThreeDeep_RiseTwoToStepOnRim() {
        assertEquals(
                Optional.of(new PitEscape.Exit(0, 0, 2, 1, 0)),
                PitEscape.exit((dx, dz) -> dx == 0 && dz == 0 ? 67 : 70),
                "Яма на 3 ниже участка (INC-78): подсыпка под себя на 2 — и шаг на край, дух выходит сам");
    }

    @Test
    void exit_WidePitEightDeep_LowestRimChosen() {
        PitEscape.Exit exit = PitEscape.exit((dx, dz) -> {
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        return 62;
                    }
                    return dx == 2 && dz == 0 ? 66 : 70;
                })
                .orElseThrow();
        assertEquals(
                3,
                exit.rise(),
                "Яма 3×3 на 8 ниже, край с одной стороны ниже (66): выход через низкий край — подъём на 3, а не на 7");
    }

    @Test
    void exit_TenDeepPit_RiseNineToRim() {
        PitEscape.Exit exit = PitEscape.exit((dx, dz) -> Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? 60 : 70)
                .orElseThrow();
        assertEquals(
                9,
                exit.rise(),
                "Дух на дне ямы 10 блоков поднимается столбом на 9 и шагает на край: самоспасение работает "
                        + "без помощника (observe24 B1/B3)");
    }

    @Test
    void exit_StairsOfOneBlock_NotTrapped() {
        assertEquals(
                Optional.empty(),
                PitEscape.exit((dx, dz) -> 70 - Math.max(0, 4 - Math.abs(dx))),
                "Спуск ступенями по блоку (шахта) — выход есть: дух не считается в яме");
    }
}

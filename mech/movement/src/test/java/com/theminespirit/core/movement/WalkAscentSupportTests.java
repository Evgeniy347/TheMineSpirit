package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.WalkAscent.Step;
import org.junit.jupiter.api.Test;

class WalkAscentSupportTests {
    @Test
    void needsFloor_FlatStepOverPit_IsTrue() {
        assertTrue(
                WalkAscent.needsFloor(Step.MOVE, false, false),
                "Ровный шаг в клетку без опоры — отказ: иначе дух идёт к точке у края шахты и падает на "
                        + "шесть блоков (окно 34, spirit-2 у -179,73,-515 к -181,73,-516, v=-0.12,-0.92, "
                        + "приземление на 67), теряет время и возвращается в неё снова. При смене правил "
                        + "хода проверь спуск с уступа: DIVE без опоры остаётся законным");
    }

    @Test
    void needsFloor_FlatStepOnFloor_IsFalse() {
        assertFalse(
                WalkAscent.needsFloor(Step.MOVE, false, true),
                "Ровный шаг по полу — обычный ход: иначе дух откажется идти по любой дороге, где под "
                        + "клеткой вместо блока стоит плита или ступень с коллизией. При смене правил хода "
                        + "проверь проход по поселению: MOVE с опорой разрешён");
    }

    @Test
    void needsFloor_FlatStepIntoWater_IsFalse() {
        assertFalse(
                WalkAscent.needsFloor(Step.MOVE, true, false),
                "Ровный шаг в воду — плавучий вход, опора не нужна: иначе дух откажется входить в пруд "
                        + "и застрянет на берегу. При смене правил хода проверь вход в воду с берега: MOVE "
                        + "в воду разрешён без опоры");
    }

    @Test
    void needsFloor_DiveWithoutFloor_IsFalse() {
        assertFalse(
                WalkAscent.needsFloor(Step.DIVE, false, false),
                "Прыжок вниз с уступа — законный спуск, опоры под целью может не быть: иначе дух "
                        + "откажется спускаться по уступам шахты и застрянет наверху. При смене правил хода "
                        + "проверь спуск с уступа: DIVE без опоры разрешён");
    }
}

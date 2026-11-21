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

    @Test
    void safeWithoutFloor_SameCellOnGround_IsTrue() {
        assertTrue(
                WalkAscent.safeWithoutFloor(true, true),
                "Довод к центру своей же клетки, стоя на полу, безопасен даже если под центром пустота: "
                        + "дух на краю площадки стоит на соседнем блоке (окно 47, spirit-2 -167,76,-491: "
                        + "«шаг MOVE без опоры» 14 раз). Иначе шаг к центру отвергается, дух не может встать "
                        + "на площадку и маршрут сходит 60 тиков спустя. При смене правила проверь шаг в "
                        + "соседнюю клетку над пропастью: он должен остаться запрещённым");
    }

    @Test
    void safeWithoutFloor_SameCellInAir_IsFalse() {
        assertFalse(
                WalkAscent.safeWithoutFloor(true, false),
                "В воздухе над своей клеткой опоры нет — довод к центру не безопасен: иначе падающий дух "
                        + "считал бы шаг выполненным и маршрут вёл бы его дальше из пустоты. При смене правила "
                        + "проверь падение над клеткой: шаг без опоры не прощается");
    }

    @Test
    void safeWithoutFloor_OtherCellOnGround_IsFalse() {
        assertFalse(
                WalkAscent.safeWithoutFloor(false, true),
                "Шаг в ДРУГУЮ клетку без опоры запрещён даже с земли: иначе дух у края шахты пойдёт в "
                        + "соседнюю клетку над пропастью и упадёт на шесть блоков (окно 34, spirit-2 у "
                        + "-179,73,-515 к -181,73,-516). При смене правила проверь живой шаг к яме: отказ");
    }
}

package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpiritSnapshotTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Point POS = new Point(10, 64, -7);

    private static SpiritSnapshot snapshot(double hp, int threats, boolean attacked) {
        return new SpiritSnapshot(SPIRIT, POS, hp, SpiritRole.WORKER, CombatMode.DEFENSIVE, threats, attacked,
                Optional.empty(), 42);
    }

    @Test
    void snapshot_ConstructorArgs_AreExposed() {
        WorkTarget tree = new WorkTarget(WorkKind.TREE, new BlockCoord(4, 64, 2));
        SpiritSnapshot withWork = new SpiritSnapshot(SPIRIT, POS, 0.5, SpiritRole.DEFENDER, CombatMode.GUARD_AREA,
                2, true, Optional.of(tree), 42);

        assertEquals(SPIRIT, withWork.id(), "id снимка — кто это: без него координатор не сопоставит решения с духом");
        assertEquals(POS, withWork.pos(), "Позиция снимка — где дух: от неё считаются расстояния до постов и целей");
        assertEquals(0.5, withWork.hpFraction(), "Доля здоровья снимка — насколько дух жив: входит в оценку обороны");
        assertEquals(SpiritRole.DEFENDER, withWork.role(),
                "Роль снимка — кто дух в поселении: защитник держит сектор, работник получает цель");
        assertEquals(CombatMode.GUARD_AREA, withWork.mode(),
                "Режим боя снимка — что дух делает сейчас: координатор не переводит занятого в работу");
        assertEquals(2, withWork.threatsNear(), "Число угроз рядом — под давлением ли дух: входит в выбор помощников");
        assertTrue(withWork.attacked(), "Флаг атаки — на духа напали: координатор шлёт помощь атакованному");
        assertEquals(Optional.of(tree), withWork.workTarget(),
                "Цель работы снимка — чем дух занят: координатор не даёт работу занятому и распределяет цели");
        assertEquals(42, withWork.lastSeenTick(), "Тик наблюдения — свежесть снимка: просроченный снимается из плана");
    }

    @Test
    void hpFraction_AboveOne_ClampedToOne() {
        assertEquals(1.0, snapshot(1.5, 0, false).hpFraction(),
                "Доля здоровья выше единицы зажимается в 1: иначе оценка обороны получит духа здоровее живого");
    }

    @Test
    void hpFraction_Negative_ClampedToZero() {
        assertEquals(0.0, snapshot(-0.5, 0, false).hpFraction(),
                "Отрицательная доля здоровья зажимается в 0: иначе оценка обороны получит духа живее мёртвого");
    }

    @Test
    void constructor_NullId_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(null, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 0, false,
                        Optional.empty(), 1),
                "Снимок без id не опознает духа: координатор не сможет сопоставить его с решениями");

        assertTrue(error.getMessage().contains("id"),
                "Сообщение называет поле id: без этого при падении не видно, какое поле снимка не заполнено");
    }

    @Test
    void constructor_NullPos_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, null, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 0, false,
                        Optional.empty(), 1),
                "Снимок без позиции не опознает, где дух: расстояния до постов и целей не посчитать");

        assertTrue(error.getMessage().contains("pos"),
                "Сообщение называет поле pos: без этого при падении не видно, какое поле снимка не заполнено");
    }

    @Test
    void constructor_NullRole_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, POS, 1, null, CombatMode.DEFENSIVE, 0, false, Optional.empty(), 1),
                "Снимок без роли не говорит, защитник дух или работник: координатор не решит, кому сектор, кому цель");
    }

    @Test
    void constructor_NullWorkTarget_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 0, false, null, 1),
                "Снимок с null вместо Optional цели не говорит, занят дух или нет: передавайте Optional.empty()");
    }

    @Test
    void attacked_TrueWithZeroThreats_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 0, true,
                        Optional.empty(), 1),
                "Атакованный без угроз противоречит снимку: координированная помощь уйдёт на ложную тревогу");

        assertTrue(error.getMessage().contains("attacked"),
                "Сообщение называет флаг attacked: без этого при падении не видно, что именно противоречит");
    }

    @Test
    void attacked_FalseWithThreats_IsAccepted() {
        SpiritSnapshot snapshot = new SpiritSnapshot(SPIRIT, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 3,
                false, Optional.empty(), 1);

        assertEquals(3, snapshot.threatsNear(),
                "Атакован при угрозах рядом не обязательно: дух может стоять под давлением, не будучи целью");
        assertTrue(!snapshot.attacked(),
                "Флаг атаки остаётся false: координированную помощь зовёт только дух, выбранный врагом целью");
    }

    @Test
    void threatsNear_Negative_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, -1, false,
                        Optional.empty(), 1),
                "Отрицательное число угроз противоречит снимку: выбор помощников получил бы неверный порядок");
    }

    @Test
    void lastSeenTick_Negative_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new SpiritSnapshot(SPIRIT, POS, 1, SpiritRole.IDLE, CombatMode.DEFENSIVE, 0, false,
                        Optional.empty(), -1),
                "Отрицательный тик наблюдения противоречит снимку: просроченность считается от него");
    }
}

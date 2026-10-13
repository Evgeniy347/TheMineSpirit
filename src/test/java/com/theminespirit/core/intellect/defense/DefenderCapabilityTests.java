package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefenderCapabilityTests {
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Point POS = new Point(10, 64, -3);

    private static DefenderCapability capable(boolean bow) {
        return new DefenderCapability(ID, POS, 1.0, 10, bow, 6.0, true);
    }

    @Test
    void capability_Fields_ReadBack() {
        DefenderCapability capability = capable(true);

        assertEquals(ID, capability.id(),
                "Приказ обороны адресуется по id: перепутанный id отправит на пост чужого духа");
        assertEquals(POS, capability.pos(),
                "Пост и дистанция считаются от позиции: неверная позиция ставит духа не на тот сектор");
        assertEquals(1.0, capability.hpFraction(),
                "Доля здоровья входит во фронтовость: неверное значение исказит распределение по силе");
        assertEquals(10, capability.armor(),
                "Броня входит во фронтовость: неверное значение отправит слабого духа на сильное направление");
        assertEquals(true, capability.bow(),
                "Флаг лука решает специализацию: неверный флаг перепутает лучника и мечника");
        assertEquals(6.0, capability.meleeDps(),
                "Урон ближнего боя отличает вооружённого от безоружного: неверный урон сломает оценку сил");
        assertEquals(true, capability.heal(),
                "Флаг лечилки решает инстинкт лечения: неверный флаг оставит раненого без лечения");
    }

    @Test
    void capability_NullId_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new DefenderCapability(null, POS, 1.0, 10, false, 6.0, true),
                "Снимок бойца без id обязан падать: иначе приказ уйдёт духу, которого нельзя опознать");
    }

    @Test
    void capability_NullPos_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new DefenderCapability(ID, null, 1.0, 10, false, 6.0, true),
                "Снимок бойца без позиции обязан падать: иначе пост и дистанция посчитаются из ничего");
    }

    @Test
    void capability_ArmorBelowZero_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenderCapability(ID, POS, 1.0, -1, false, 6.0, true),
                "Отрицательная броня обязана падать: иначе фронтовость выйдет за шкалу и сравнение сломается");
    }

    @Test
    void capability_ArmorAboveTwenty_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenderCapability(ID, POS, 1.0, 21, false, 6.0, true),
                "Броня выше 20 обязана падать: шкала брони духа кончается на 20, большее — ошибка источника");
    }

    @Test
    void capability_NegativeDps_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new DefenderCapability(ID, POS, 1.0, 10, false, -0.5, true),
                "Отрицательный урон обязан падать: иначе безоружный дух посчитается бьющим");
    }

    @Test
    void capability_HpAboveOne_ClampedToOne() {
        DefenderCapability capability = new DefenderCapability(ID, POS, 1.5, 10, false, 6.0, true);

        assertEquals(1.0, capability.hpFraction(),
                "Доля здоровья зажимается в 0..1 как в SpiritSnapshot: иначе фронтовость выйдет за шкалу");
    }

    @Test
    void capability_HpBelowZero_ClampedToZero() {
        DefenderCapability capability = new DefenderCapability(ID, POS, -0.5, 10, false, 6.0, true);

        assertEquals(0.0, capability.hpFraction(),
                "Доля здоровья зажимается в 0..1 как в SpiritSnapshot: иначе фронтовость выйдет за шкалу");
    }

    @Test
    void role_BowPresent_Archer() {
        assertEquals(DefenderRole.ARCHER, capable(true).role(),
                "Дух с луком — стрелок: иначе крипер и скелет останутся без приоритетного бойца");
    }

    @Test
    void role_NoBow_MeleeDespiteArmorAndDps() {
        DefenderCapability strong = new DefenderCapability(ID, POS, 1.0, 20, false, 12.0, true);

        assertEquals(DefenderRole.MELEE, strong.role(),
                "Только лук меняет роль: броня и урон решают фронтовость, а не специализацию");
    }

    @Test
    void frontRank_FullArmorAndHealth_EqualsOne() {
        DefenderCapability best = new DefenderCapability(ID, POS, 1.0, 20, false, 6.0, true);

        assertEquals(1.0, best.frontRank(),
                "Полная броня и здоровье дают фронтовость 1: иначе лучший боец не встанет на сильное направление");
    }

    @Test
    void frontRank_NoArmorNoHealth_EqualsZero() {
        DefenderCapability worst = new DefenderCapability(ID, POS, 0.0, 0, false, 0.0, false);

        assertEquals(0.0, worst.frontRank(),
                "Нулевые броня и здоровье дают фронтовость 0: иначе раздетый дух полезет в лоб");
    }

    @Test
    void frontRank_BetterArmor_HigherRank() {
        DefenderCapability light = new DefenderCapability(ID, POS, 0.8, 4, false, 6.0, true);
        DefenderCapability heavy = new DefenderCapability(ID, POS, 0.8, 16, false, 6.0, true);

        assertTrue(light.frontRank() < heavy.frontRank(),
                "При равном здоровье броня решает фронтовость: иначе слабый доспех пойдёт первым на крипера");
    }
}

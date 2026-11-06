package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThreatBearingTests {
    static final Point CENTER = new Point(0, 64, 0);
    static final EnemyProfiles PROFILES = new EnemyProfiles(
            Map.of(
                    "minecraft:zombie",
                    new EnemyProfile("minecraft:zombie", 20, 3, 0.23, false, false, false, false, 1),
                    "minecraft:creeper",
                    new EnemyProfile("minecraft:creeper", 20, 0, 0.25, false, true, false, false, 2)),
            new EnemyProfile("default", 20, 4, 1, false, false, false, false, 2));

    static EnemyFact zombie(double x, double z) {
        return new EnemyFact("minecraft:zombie", UUID.randomUUID().toString(), new Point(x, 64, z), 1.0, 0, true, true);
    }

    @Test
    void sum_SingleEnemy_OneNonZeroSector() {
        List<DirectionThreat> threats = ThreatBearing.sum(List.of(zombie(10, 0)), CENTER, PROFILES);

        assertEquals(
                1,
                threats.size(),
                "Один враг даёт ровно один ненулевой сектор: иначе сила размажется и посты встанут не туда");
        assertEquals(
                1,
                threats.get(0).enemies(),
                "Сектор с одним врагом считает одного: иначе распределение выделит лишний пост");
        assertEquals(
                20 * 3.0,
                threats.get(0).strength(),
                "Сила сектора — hp × dps × count профиля: иначе вес направления не отражает опасность");
    }

    @Test
    void sum_TwoEnemiesOneSector_StrengthsAdd() {
        List<DirectionThreat> threats = ThreatBearing.sum(List.of(zombie(10, 0), zombie(12, 1)), CENTER, PROFILES);

        assertEquals(1, threats.size(), "Два врага одного сектора дают один сектор: иначе направление раздвоится");
        assertEquals(
                2,
                threats.get(0).enemies(),
                "Два врага одного сектора суммируются в числе: иначе вес направления занижен вдвое");
        assertEquals(
                2 * 20 * 3.0,
                threats.get(0).strength(),
                "Сила сектора суммирует вклад обоих: иначе второй враг не усилит направление");
    }

    @Test
    void sum_EnemiesOppositeSides_TwoSectors() {
        List<DirectionThreat> threats = ThreatBearing.sum(List.of(zombie(10, 0), zombie(-10, 0)), CENTER, PROFILES);

        assertEquals(
                2,
                threats.size(),
                "Враги с противоположных сторон дают два сектора: иначе защитники встанут только на одно");
        assertTrue(
                threats.get(0).bearing() < threats.get(1).bearing(),
                "Сектора упорядочены по азимуту: иначе посты и распределение зависят от порядка входа");
    }

    @Test
    void sum_CreeperWithZeroDps_CountsByFloor() {
        EnemyFact creeper = new EnemyFact(
                "minecraft:creeper", UUID.randomUUID().toString(), new Point(10, 64, 0), 1.0, 0, true, true);

        List<DirectionThreat> threats = ThreatBearing.sum(List.of(creeper), CENTER, PROFILES);

        assertEquals(
                20 * 0.1,
                threats.get(0).strength(),
                "Крипер с нулевым dps входит в вес через пол MIN_DPS: иначе взрывное направление весит ноль");
    }

    @Test
    void sum_UnknownType_UsesDefaultProfile() {
        EnemyFact beast = new EnemyFact(
                "minecraft:modded_beast", UUID.randomUUID().toString(), new Point(10, 64, 0), 1.0, 0, true, true);

        List<DirectionThreat> threats = ThreatBearing.sum(List.of(beast), CENTER, PROFILES);

        assertEquals(
                20 * 4.0,
                threats.get(0).strength(),
                "Неизвестный тип оценивается профилем default: иначе модовый враг не усилит направление");
    }

    @Test
    void sum_EmptyList_ReturnsEmpty() {
        assertEquals(
                List.of(),
                ThreatBearing.sum(List.of(), CENTER, PROFILES),
                "Без врагов секторов нет: иначе посты встанут по нулевым угрозам");
    }

    @Test
    void sum_NullCenter_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ThreatBearing.sum(List.of(zombie(10, 0)), null, PROFILES),
                "Сектора без центра обязаны падать: иначе азимуты посчитаются из ничего");
    }

    @Test
    void threat_NegativeStrength_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DirectionThreat(1.0, -1.0, 1),
                "Отрицательная сила сектора обязана падать: иначе вес направления исказит распределение");
    }
}

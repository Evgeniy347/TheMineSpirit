package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstinctRulesTests {
    private static final UUID SELF_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final EnemyFact ZOMBIE = new EnemyFact(
            "minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", new Point(5, 64, 0), 1.0, 0, true, true);
    private static final EnemyFact SKELETON = new EnemyFact(
            "minecraft:skeleton", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", new Point(10, 64, 0), 1.0, 0, true, true);
    private static final EnemyFact CREEPER = new EnemyFact(
            "minecraft:creeper", "cccccccc-cccc-cccc-cccc-cccccccccccc", new Point(6, 64, 0), 1.0, 0, true, true);
    private static final RankedTarget ZOMBIE_TARGET = new RankedTarget(ZOMBIE, TargetClass.MELEE, 0, 5.0);
    private static final RankedTarget SKELETON_TARGET = new RankedTarget(SKELETON, TargetClass.SKELETON, 0, 10.0);
    private static final RankedTarget CREEPER_TARGET = new RankedTarget(CREEPER, TargetClass.CREEPER, 0, 6.0);

    private static DefenderCapability melee(double hp, boolean heal) {
        return new DefenderCapability(SELF_ID, new Point(0, 64, 0), hp, 10, false, 6.0, heal);
    }

    private static DefenderCapability archerAlly() {
        return new DefenderCapability(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), new Point(2, 64, 0), 1.0, 10, true, 6.0, true);
    }

    private static DefenderCapability meleeAlly() {
        return new DefenderCapability(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                new Point(0, 64, 8),
                1.0,
                10,
                false,
                6.0,
                true);
    }

    @Test
    void decide_WoundedWithHeal_ReturnsHeal() {
        InstinctDecision decision = InstinctRules.decide(
                melee(0.2, true),
                ZOMBIE_TARGET,
                List.of(ZOMBIE),
                List.of(archerAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.HEAL,
                decision.stance(),
                "Раненый с лечилкой лечится: иначе дух с зельем гибнет вместо отхода за лечением");
        assertEquals(
                Optional.empty(),
                decision.handOff(),
                "Лечение без передачи цели: дух сам возвращается в бой после лечения");
    }

    @Test
    void decide_WoundedWithoutHeal_ReturnsWithdrawWithHandoff() {
        InstinctDecision decision = InstinctRules.decide(
                melee(0.2, false),
                ZOMBIE_TARGET,
                List.of(ZOMBIE),
                List.of(archerAlly(), meleeAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.WITHDRAW,
                decision.stance(),
                "Раненый без лечилки отходит: иначе дух дерётся до смерти вместо передачи цели");
        assertEquals(
                Optional.of(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                decision.handOff(),
                "Отход передаёт цель ближайшему способному: иначе цель останется без присмотра");
    }

    @Test
    void decide_MeleeVsCreeper_ReturnsStrikeAndRetreat() {
        InstinctDecision decision = InstinctRules.decide(
                melee(1.0, false),
                CREEPER_TARGET,
                List.of(CREEPER),
                List.of(archerAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.STRIKE_AND_RETREAT,
                decision.stance(),
                "Мечник бьёт крипера наскоком: иначе либо стоит впритык под взрывом, либо не бьёт вовсе");
        assertEquals(
                7.0,
                decision.holdDistance().orElseThrow(),
                "Держание равно радиусу политики крипера: свой радиус рассинхронизирует инстинкт с рефлексом");
    }

    @Test
    void decide_MeleeVsSkeletonWithoutArcher_ReturnsRushRanged() {
        InstinctDecision decision = InstinctRules.decide(
                melee(1.0, false),
                SKELETON_TARGET,
                List.of(SKELETON),
                List.of(meleeAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.RUSH_RANGED,
                decision.stance(),
                "Мечник без лучника быстро сближается со скелетом: иначе обстрел останется без ответа");
    }

    @Test
    void decide_MeleeVsSkeletonWithArcher_ReturnsEngage() {
        InstinctDecision decision = InstinctRules.decide(
                melee(1.0, false),
                SKELETON_TARGET,
                List.of(SKELETON),
                List.of(archerAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.ENGAGE,
                decision.stance(),
                "Мечник при живом лучнике не мчится к скелету: скелет — цель лучника, мечник держит ближних");
    }

    @Test
    void decide_MeleeVsZombieWithSkeletonNear_ReturnsCoverEngage() {
        EnemyFact nearSkeleton =
                new EnemyFact("minecraft:skeleton", SKELETON.id(), new Point(8, 64, 0), 1.0, 0, true, true);

        InstinctDecision decision = InstinctRules.decide(
                melee(1.0, false),
                ZOMBIE_TARGET,
                List.of(ZOMBIE, nearSkeleton),
                List.of(meleeAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.COVER_ENGAGE,
                decision.stance(),
                "При скелете рядом мечник дерётся из-за укрытия: иначе стрелы скелета бьют в спину");
        assertEquals(
                Optional.of(new Point(5, 64, 0)),
                decision.cover(),
                "Приём «зомби между»: цель встаёт между духом и скелетом, и стрелы попадают в зомби");
    }

    @Test
    void decide_MeleeVsZombieWithSkeletonFar_ReturnsEngage() {
        EnemyFact farSkeleton =
                new EnemyFact("minecraft:skeleton", SKELETON.id(), new Point(30, 64, 0), 1.0, 0, true, true);

        InstinctDecision decision = InstinctRules.decide(
                melee(1.0, false),
                ZOMBIE_TARGET,
                List.of(ZOMBIE, farSkeleton),
                List.of(meleeAlly()),
                Set.of(),
                DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.ENGAGE,
                decision.stance(),
                "Далёкий скелет не требует прикрытия: иначе боец жмётся к укрытию без обстрела");
        assertEquals(
                Optional.empty(),
                decision.cover(),
                "Без скелета рядом укрытие не ищется: иначе приказ сошлётся на случайную точку");
    }

    @Test
    void decide_ArcherVsZombie_ReturnsHoldDistance() {
        DefenderCapability archer = new DefenderCapability(SELF_ID, new Point(0, 64, 0), 1.0, 10, true, 6.0, true);

        InstinctDecision decision = InstinctRules.decide(
                archer, ZOMBIE_TARGET, List.of(ZOMBIE), List.of(meleeAlly()), Set.of(), DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.HOLD_DISTANCE,
                decision.stance(),
                "Лучник держит дистанцию: иначе стрелок ввязывается в ближний бой вместо обстрела");
    }

    @Test
    void decide_ArcherVsCreeper_ReturnsHoldDistance() {
        DefenderCapability archer = new DefenderCapability(SELF_ID, new Point(0, 64, 0), 1.0, 10, true, 6.0, true);

        InstinctDecision decision = InstinctRules.decide(
                archer, CREEPER_TARGET, List.of(CREEPER), List.of(meleeAlly()), Set.of(), DefenseProfileTests.PROFILE);

        assertEquals(
                CombatStance.HOLD_DISTANCE,
                decision.stance(),
                "Лучник расстреливает крипера издали: иначе стрелок рискует взрывом вместо безопасного обстрела");
    }

    @Test
    void decide_NullSelf_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> InstinctRules.decide(
                        null,
                        ZOMBIE_TARGET,
                        List.of(ZOMBIE),
                        List.of(meleeAlly()),
                        Set.of(),
                        DefenseProfileTests.PROFILE),
                "Стойка без духа обязана падать: иначе приказ уйдёт без адресата");
    }
}

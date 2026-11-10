package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.orderOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.plan;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.single;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.verify;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TeamDefenseGearScenarioTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseGearCases#gearCases")
    void gear_Scenarios_AdjustHelpToEquipment(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.theminespirit.core.intellect.defense.TeamDefenseGearCases#stanceCases")
    void stance_Scenarios_MatchEnemyType(TeamDefenseScenarios.Case scenario) {
        verify(scenario);
    }

    @Test
    void stance_MixedSquad_MeleeEngagesWhileWoundedHeals() {
        UUID woundedId = UUID.nameUUIDFromBytes("wounded".getBytes(StandardCharsets.UTF_8));
        UUID healthyId = UUID.nameUUIDFromBytes("healthy".getBytes(StandardCharsets.UTF_8));
        List<DefenseMember> members = List.of(
                new DefenseMember(
                        woundedId, new Point(-100, 64, 0), 0.2, 10, false, 6.0, 1.0, Set.of(DefensePotion.HEAL)),
                new DefenseMember(healthyId, new Point(-100, 64, 1), 1.0, 10, false, 6.0, 1.0, Set.of()));
        List<EnemyFact> enemies =
                List.of(new EnemyFact("minecraft:zombie", "mob", new Point(-120, 64, 0), 1.0, 0, true, true, 1));

        TeamDefensePlan plan = TeamDefensePlanner.plan(
                TeamDefenseScenarios.CENTER,
                members,
                enemies,
                TeamDefenseScenarios.PROFILES,
                TeamDefenseScenarios.PROFILE,
                TeamDefenseScenarios.WEIGHTS);

        Set<CombatStance> stances =
                new HashSet<>(orderOf(plan, "S1").memberStances().values());
        assertEquals(
                Set.of(CombatStance.HEAL, CombatStance.ENGAGE),
                stances,
                "В смешанной паре каждый решает по себе: раненый лечится, здоровый прикрывает, иначе оба гибнут");
    }

    @Test
    void potion_StrengthAgainstCreeper_IsDrunkBeforeStrike() {
        TeamDefensePlan plan = plan(caseOf(
                "H11",
                "мечник с зельем силы против крипера: пьёт силу перед ударом",
                List.of(single(0, -90, 1.0, 10, false, 6, DefensePotion.STRENGTH)),
                List.of(TeamDefenseScenarios.creepers(1, 0, -100)),
                "S1",
                "ENGAGE"));

        assertEquals(
                Map.of(orderOf(plan, "S1").memberStances().keySet().iterator().next(), DefensePotion.STRENGTH),
                orderOf(plan, "S1").memberPotions(),
                "Сила пьётся против крипера: иначе зелье пропадает, а удар не пробивает угрозу");
    }

    @Test
    void potion_SpeedAgainstSkeleton_IsDrunkToCloseDistance() {
        TeamDefensePlan plan = plan(caseOf(
                "H12",
                "мечник с зельем скорости против скелета: пьёт скорость, чтобы сблизиться",
                List.of(single(0, -90, 1.0, 10, false, 6, DefensePotion.SPEED)),
                List.of(TeamDefenseScenarios.skeletons(1, 0, -100)),
                "S1",
                "ENGAGE"));

        assertEquals(
                Map.of(orderOf(plan, "S1").memberStances().keySet().iterator().next(), DefensePotion.SPEED),
                orderOf(plan, "S1").memberPotions(),
                "Скорость пьётся против лучника: иначе мечник не догонит скелета и попадёт под стрелы");
    }

    @Test
    void potion_FullHealthHeal_IsNotWasted() {
        TeamDefensePlan plan = plan(caseOf(
                "H13",
                "полный дух с лечилкой против зомби: зелье не тратит",
                List.of(single(0, -90, 1.0, 10, false, 6, DefensePotion.HEAL)),
                List.of(zombies(1, 0, -100)),
                "S1",
                "ENGAGE"));

        assertTrue(
                orderOf(plan, "S1").memberPotions().isEmpty(),
                "Здоровый не пьёт лечилку: иначе зелье уходит впустую и не остаётся на ранение");
    }
}

package com.theminespirit.core.intellect.defense;

import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.caseOf;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.pair;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.single;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.singleFood;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.stanceCase;
import static com.theminespirit.core.intellect.defense.TeamDefenseScenarios.zombies;

import java.util.List;
import java.util.Set;

final class TeamDefenseGearCases {
    private TeamDefenseGearCases() {}

    static List<TeamDefenseScenarios.Case> gearCases() {
        return List.of(
                caseOf(
                        "G01",
                        "4 моба, полная броня и меч: пара держит сама",
                        List.of(pair(-100, 0, 1.0, 10, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "STAY",
                        "S3",
                        "STAY"),
                caseOf(
                        "G02",
                        "4 моба, брони нет: пара зовёт помощь",
                        List.of(pair(-100, 0, 1.0, 0, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G03",
                        "4 моба, половина брони: ёмкости уже не хватает",
                        List.of(pair(-100, 0, 1.0, 5, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G04",
                        "4 моба, полная броня: запас прочности закрывает угрозу",
                        List.of(pair(-100, 0, 1.0, 20, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "STAY",
                        "S3",
                        "STAY"),
                caseOf(
                        "G05",
                        "4 моба, крепкое оружие: пара держит сама",
                        List.of(pair(-100, 0, 1.0, 10, 4), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "STAY",
                        "S3",
                        "STAY"),
                caseOf(
                        "G06",
                        "4 моба, слабое оружие: ёмкости мало, помощь идёт",
                        List.of(pair(-100, 0, 1.0, 10, 2), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G07",
                        "4 моба, пара ранена наполовину: помощь идёт",
                        List.of(pair(-100, 0, 0.5, 10, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G08",
                        "4 моба, пара в четверть хп без лечилки: отходит к центру",
                        List.of(pair(-100, 0, 0.2, 10, 6), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "RETREAT",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G09",
                        "один моб, пара с лечилкой в четверть хп: лечится, а не умирает на месте",
                        List.of(pair(-100, 0, 0.2, 10, 6, DefensePotion.HEAL), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(1, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "STAY",
                        "S3",
                        "STAY"),
                caseOf(
                        "G10",
                        "4 моба, пара сыта: дерётся",
                        List.of(pair(-100, 0), pair(-50, 0), pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "ENGAGE",
                        "S2",
                        "STAY",
                        "S3",
                        "STAY"),
                caseOf(
                        "G11",
                        "4 моба, пара голодна: сперва ест, бой подождёт",
                        List.of(
                                new TeamDefenseScenarios.SquadSpec("h", -100, 0, 2, 1.0, 10, false, 6.0, 0.5, Set.of()),
                                pair(-50, 0),
                                pair(0, 0)),
                        List.of(zombies(4, -120, 0)),
                        "S1",
                        "EAT",
                        "S2",
                        "REINFORCE",
                        "S3",
                        "STAY"),
                caseOf(
                        "G12",
                        "мобов нет, один дух голоден: идёт есть",
                        List.of(singleFood(-100, 0, 0.4), pair(-50, 0)),
                        List.of(),
                        "S1",
                        "EAT",
                        "S2",
                        "STAY"),
                caseOf(
                        "G13",
                        "мобов нет, один дух сыт: стоит",
                        List.of(singleFood(-100, 0, 1.0), pair(-50, 0)),
                        List.of(),
                        "S1",
                        "STAY",
                        "S2",
                        "STAY"));
    }

    static List<TeamDefenseScenarios.Case> stanceCases() {
        return List.of(
                stanceCase(
                        "H01",
                        "зомби и дух с полным хп: обычный бой",
                        List.of(single(0, -90, 1.0, 10, false, 6)),
                        List.of(zombies(1, 0, -100)),
                        CombatStance.ENGAGE,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H02",
                        "паук и дух с полным хп: обычный бой",
                        List.of(single(0, -90, 1.0, 10, false, 6)),
                        List.of(TeamDefenseScenarios.spiders(1, 0, -100)),
                        CombatStance.ENGAGE,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H03",
                        "зомби и дух в четверть хп с лечилкой: лечится",
                        List.of(single(0, -90, 0.2, 10, false, 6, DefensePotion.HEAL)),
                        List.of(zombies(1, 0, -100)),
                        CombatStance.HEAL,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H04",
                        "зомби и дух в четверть хп без лечилки: отходит",
                        List.of(single(0, -90, 0.2, 10, false, 6)),
                        List.of(zombies(1, 0, -100)),
                        CombatStance.WITHDRAW,
                        "S1",
                        "RETREAT"),
                stanceCase(
                        "H05",
                        "скелет и мечник без лучника: сближается",
                        List.of(single(0, -90, 1.0, 10, false, 6)),
                        List.of(TeamDefenseScenarios.skeletons(1, 0, -100)),
                        CombatStance.RUSH_RANGED,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H06",
                        "скелет и лучник: держит дистанцию",
                        List.of(single(0, -90, 1.0, 10, true, 3)),
                        List.of(TeamDefenseScenarios.skeletons(1, 0, -100)),
                        CombatStance.HOLD_DISTANCE,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H07",
                        "крипер и мечник: удар и отскок",
                        List.of(single(0, -90, 1.0, 10, false, 6)),
                        List.of(TeamDefenseScenarios.creepers(1, 0, -100)),
                        CombatStance.STRIKE_AND_RETREAT,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H08",
                        "крипер и лучник: держит дистанцию",
                        List.of(single(0, -90, 1.0, 10, true, 3)),
                        List.of(TeamDefenseScenarios.creepers(1, 0, -100)),
                        CombatStance.HOLD_DISTANCE,
                        "S1",
                        "ENGAGE"),
                stanceCase(
                        "H09",
                        "зомби и скелет одновременно: мечник без лучника сперва сближается со стрелком",
                        List.of(single(0, -90, 1.0, 10, false, 6)),
                        List.of(zombies(1, 0, -100), TeamDefenseScenarios.skeletons(1, 10, -100)),
                        CombatStance.RUSH_RANGED,
                        "S1",
                        "ENGAGE"));
    }
}

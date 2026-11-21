package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.api.Point;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetRankerTests {
    private static EnemyFact enemy(String type, String id, double x) {
        return new EnemyFact(type, id, new Point(x, 64, 0), 1.0, 0, true, true);
    }

    @Test
    void rank_Archer_PicksCreeperOverNearZombie() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2),
                enemy("minecraft:creeper", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 8));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.ARCHER, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                ranked.get(0).enemy().id(),
                "Лучник бьёт крипера первым даже рядом с зомби: взрыв у построек опаснее ближнего боя"
                        + " Иначе тест rank_Archer_PicksCreeperOverNearZombie перестанет ловить эту поломку. При смене rank_Archer_PicksCreeperOverNearZombie проверь это утверждение.");
        assertEquals(
                TargetClass.CREEPER,
                ranked.get(0).cls(),
                "Первая цель лучника обязана иметь класс CREEPER: иначе приоритет посчитан не по таблице"
                        + " При смене rank_Archer_PicksCreeperOverNearZombie проверь это утверждение.");
    }

    @Test
    void rank_Melee_PicksZombieOverCreeper() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:creeper", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 2),
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 8));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.MELEE, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                ranked.get(0).enemy().id(),
                "Мечник при живом лучнике не идёт на крипера: взрыв убьёт мечника раньше, чем подойдёт лучник"
                        + " Иначе тест rank_Melee_PicksZombieOverCreeper перестанет ловить эту поломку. При смене rank_Melee_PicksZombieOverCreeper проверь это утверждение.");
        assertEquals(
                2,
                ranked.get(1).rank(),
                "Крипер вне порядка мечника уходит в хвост: иначе мечник выберет взрыв вместо ближнего врага"
                        + " При смене rank_Melee_PicksZombieOverCreeper проверь это утверждение.");
    }

    @Test
    void rank_MeleeWithoutArcher_PicksSkeletonFirst() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2),
                enemy("minecraft:skeleton", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 8));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.MELEE, false, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                ranked.get(0).enemy().id(),
                "Мечник без лучника сближается со скелетом первым: иначе обстрел останется без ответа"
                        + " При смене rank_MeleeWithoutArcher_PicksSkeletonFirst проверь это утверждение.");
    }

    @Test
    void rank_DistanceTie_BreaksById() {
        List<EnemyFact> enemies = List.of(
                new EnemyFact(
                        "minecraft:zombie",
                        "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                        new Point(3, 64, 4),
                        1.0,
                        0,
                        true,
                        true),
                new EnemyFact(
                        "minecraft:zombie",
                        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                        new Point(-3, 64, -4),
                        1.0,
                        0,
                        true,
                        true));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.MELEE, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                ranked.get(0).distance(),
                ranked.get(1).distance(),
                "Подготовка ничьей: обе цели обязаны быть на равной дистанции от начала координат"
                        + " Иначе тест rank_DistanceTie_BreaksById перестанет ловить эту поломку. При смене rank_DistanceTie_BreaksById проверь это утверждение.");
        assertEquals(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                ranked.get(0).enemy().id(),
                "Ничья по дистанции решается меньшим id: иначе порядок целей мигает между пересборками"
                        + " При смене rank_DistanceTie_BreaksById проверь это утверждение.");
    }

    @Test
    void rank_ClassOutsidePriority_GoesLast() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 8),
                enemy("minecraft:phantom", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 2));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.ARCHER, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                ranked.get(0).enemy().id(),
                "Летающий вне приоритета уходит в хвост: иначе непроверенный класс вытеснит ближнюю цель"
                        + " При смене rank_ClassOutsidePriority_GoesLast проверь это утверждение.");
        assertEquals(
                4,
                ranked.get(1).rank(),
                "Ранг внеприоритетного класса равен длине порядка: иначе хвост смешается с известными классами"
                        + " При смене rank_ClassOutsidePriority_GoesLast проверь это утверждение.");
    }

    @Test
    void rank_UnknownType_RankedAsFallbackClass() {
        List<EnemyFact> enemies = List.of(enemy("minecraft:modded_beast", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2));

        List<RankedTarget> ranked = TargetRanker.rank(DefenderRole.MELEE, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                TargetClass.MELEE,
                ranked.get(0).cls(),
                "Ранг считается по явной карте и fallback без профилей: карта накрывает все типы enemies.json "
                        + "(сторож п. 3), модовый враг идёт классом fallback"
                        + " Иначе тест rank_UnknownType_RankedAsFallbackClass перестанет ловить эту поломку. При смене rank_UnknownType_RankedAsFallbackClass проверь это утверждение.");
    }

    @Test
    void rank_EmptyList_ReturnsEmpty() {
        assertEquals(
                List.of(),
                TargetRanker.rank(DefenderRole.MELEE, true, List.of(), DefenseProfileTests.PROFILE),
                "Без врагов порядок пуст: иначе планировщик выдаст приказ на несуществующую цель"
                        + " При смене rank_EmptyList_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void rank_NullEnemies_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TargetRanker.rank(DefenderRole.MELEE, true, null, DefenseProfileTests.PROFILE),
                "Порядок без списка врагов обязан падать: иначе молчаливый приказ уйдёт в никуда"
                        + " При смене rank_NullEnemies_Throws проверь это утверждение.");
    }

    @Test
    void best_MixedThreats_ReturnsFirstRanked() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 2),
                enemy("minecraft:creeper", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 8));

        Optional<RankedTarget> best =
                TargetRanker.best(DefenderRole.ARCHER, true, enemies, DefenseProfileTests.PROFILE);

        assertEquals(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                best.orElseThrow().enemy().id(),
                "Лучшая цель лучника — крипер: иначе первая атака уйдёт не в главную угрозу"
                        + " При смене best_MixedThreats_ReturnsFirstRanked проверь это утверждение.");
    }

    @Test
    void best_EmptyList_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                TargetRanker.best(DefenderRole.MELEE, true, List.of(), DefenseProfileTests.PROFILE),
                "Без врагов лучшей цели нет: иначе планировщик выдаст приказ на несуществующую цель"
                        + " При смене best_EmptyList_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void rank_Distance_MeasuredFromOriginAsDeterministicTiebreak() {
        EnemyFact enemy = enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 3);

        List<RankedTarget> ranked =
                TargetRanker.rank(DefenderRole.MELEE, true, List.of(enemy), DefenseProfileTests.PROFILE);

        assertEquals(
                new Point(3, 64, 0).distanceTo(new Point(0, 0, 0)),
                ranked.get(0).distance(),
                "Дистанция — от начала координат: общий порядок отряда детерминирован, близость к духу учитывают "
                        + "посты распределителя и передача цели"
                        + " Иначе тест rank_Distance_MeasuredFromOriginAsDeterministicTiebreak перестанет ловить эту поломку. При смене rank_Distance_MeasuredFromOriginAsDeterministicTiebreak проверь это утверждение.");
        assertTrue(
                ranked.get(0).distance() > 0,
                "Дистанция положительна для вынесенной цели: иначе тай-брейк не различит дальних врагов"
                        + " При смене rank_Distance_MeasuredFromOriginAsDeterministicTiebreak проверь это утверждение.");
    }
}

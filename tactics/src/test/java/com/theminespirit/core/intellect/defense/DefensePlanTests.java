package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DefensePlanTests {
    private static final Point CENTER = new Point(0, 64, 0);
    private static final UUID ARCHER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MELEE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static DefenderCapability archer(UUID id, double hp) {
        return new DefenderCapability(id, new Point(1, 64, 1), hp, 10, true, 6.0, true);
    }

    private static DefenderCapability melee(UUID id, int armor, double hp) {
        return new DefenderCapability(id, new Point(-1, 64, 1), hp, armor, false, 6.0, false);
    }

    private static EnemyFact enemy(String type, String id, double x) {
        return new EnemyFact(type, id, new Point(x, 64, 0), 1.0, 0, true, true);
    }

    private static CoordinatorState state() {
        return new CoordinatorState(Optional.of(CENTER), 16.0, List.of(), 0);
    }

    private static DefenderOrder orderOf(DefensePlan plan, UUID id) {
        return plan.orders().stream()
                .filter(order -> order.defender().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void plan_NoDefendedPoint_ReturnsEmptyPlan() {
        CoordinatorState homeless = new CoordinatorState(Optional.empty(), 0, List.of(), 0);

        DefensePlan plan = DefensePlanner.plan(
                homeless,
                List.of(archer(ARCHER_ID, 1.0)),
                List.of(enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5)),
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        assertEquals(
                Optional.empty(),
                plan.defendedPoint(),
                "Без защищаемой точки плана нет: иначе посты встанут вокруг ничего");
        assertEquals(List.of(), plan.posts(), "Без защищаемой точки постов нет: иначе приказ уведёт духа от поселения");
        assertEquals(List.of(), plan.orders(), "Без защищаемой точки приказов нет: иначе дух получит цель без рубежа");
    }

    @Test
    void plan_PointAndEnemies_EveryPostHasOrder() {
        List<DefenderCapability> defenders = List.of(archer(ARCHER_ID, 1.0), melee(MELEE_ID, 10, 1.0));
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5),
                enemy("minecraft:skeleton", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 8));

        DefensePlan plan = DefensePlanner.plan(
                state(), defenders, enemies, ThreatBearingTests.PROFILES, DefenseProfileTests.PROFILE);

        assertEquals(
                Optional.of(CENTER),
                plan.defendedPoint(),
                "План привязан к защищаемой точке: иначе посты не с чем сверять");
        assertEquals(
                plan.posts().size(),
                plan.orders().size(),
                "У каждого поста свой приказ: иначе дух стоит без цели или воюет без поста");
        assertEquals(
                plan.posts().stream().map(DefenseAssignment::defender).sorted().toList(),
                plan.orders().stream().map(DefenderOrder::defender).sorted().toList(),
                "Посты и приказы накрывают одних духов: иначе часть отряда без управления");
    }

    @Test
    void plan_MeleeWithArcher_NeverTakesCreeper() {
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5),
                enemy("minecraft:creeper", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 30));

        DefensePlan plan = DefensePlanner.plan(
                state(),
                List.of(archer(ARCHER_ID, 1.0), melee(MELEE_ID, 10, 1.0)),
                enemies,
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        assertEquals(
                "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                orderOf(plan, MELEE_ID).target().orElseThrow().id(),
                "Мечник при живом лучнике не берёт крипера: взрыв убьёт мечника раньше обстрела");
        assertEquals(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                orderOf(plan, ARCHER_ID).target().orElseThrow().id(),
                "Далёкий крипер — цель лучника: иначе взрыв подойдёт к точке без обстрела");
    }

    @Test
    void plan_LoneCreeperNoArcher_BestArmorMeleeTakesIt() {
        UUID weak = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID strong = UUID.fromString("44444444-4444-4444-4444-444444444444");
        List<EnemyFact> enemies = List.of(enemy("minecraft:creeper", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 20));

        DefensePlan plan = DefensePlanner.plan(
                state(),
                List.of(melee(weak, 0, 1.0), melee(strong, 20, 1.0)),
                enemies,
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        DefenderOrder champion = orderOf(plan, strong);
        assertEquals(
                "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                champion.target().orElseThrow().id(),
                "Без лучника один мечник в лучшей броне идёт наскоком на крипера: иначе взрыв никто не встретит");
        assertEquals(
                CombatStance.STRIKE_AND_RETREAT,
                champion.stance(),
                "Наскок на крипера — удар и отскок: иначе мечник встанет впритык под взрывом");
        assertEquals(
                Optional.empty(),
                orderOf(plan, weak).target(),
                "Второй мечник держится дальше от крипера: на взрыв идёт ровно один");
    }

    @Test
    void plan_WoundedDefender_HandsOffTarget() {
        UUID wounded = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<DefenderCapability> defenders = List.of(melee(wounded, 10, 0.2), melee(MELEE_ID, 10, 1.0));
        List<EnemyFact> enemies = List.of(enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5));

        DefensePlan plan = DefensePlanner.plan(
                state(), defenders, enemies, ThreatBearingTests.PROFILES, DefenseProfileTests.PROFILE);

        DefenderOrder order = orderOf(plan, wounded);
        assertEquals(
                CombatStance.WITHDRAW,
                order.stance(),
                "Раненый без лечилки отходит: иначе дух дерётся до смерти вместо передачи цели");
        assertEquals(
                Optional.of(MELEE_ID),
                order.handOff(),
                "Отход передаёт цель уцелевшему: иначе цель останется без присмотра");
    }

    @Test
    void plan_ShuffledInput_SamePlan() {
        List<DefenderCapability> defenders = List.of(archer(ARCHER_ID, 1.0), melee(MELEE_ID, 10, 1.0));
        List<EnemyFact> enemies = List.of(
                enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5),
                enemy("minecraft:skeleton", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", 8),
                enemy("minecraft:creeper", "cccccccc-cccc-cccc-cccc-cccccccccccc", 12));
        List<DefenderCapability> shuffledDefenders = new ArrayList<>(defenders);
        List<EnemyFact> shuffledEnemies = new ArrayList<>(enemies);
        Collections.shuffle(shuffledDefenders, new Random(23));
        Collections.shuffle(shuffledEnemies, new Random(29));

        assertEquals(
                DefensePlanner.plan(
                        state(), defenders, enemies, ThreatBearingTests.PROFILES, DefenseProfileTests.PROFILE),
                DefensePlanner.plan(
                        state(),
                        shuffledDefenders,
                        shuffledEnemies,
                        ThreatBearingTests.PROFILES,
                        DefenseProfileTests.PROFILE),
                "План не зависит от порядка входа: иначе приказы мигают при каждом новом снимке мира");
    }

    @Test
    void plan_TooManyEnemies_TrimmedToMax() {
        List<DefenderCapability> defenders = new ArrayList<>();
        List<EnemyFact> enemies = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            UUID id = UUID.fromString("12345678-1234-1234-1234-%012d".formatted(index));
            defenders.add(new DefenderCapability(id, new Point(0, 64, 0), 1.0, 10, index % 2 == 0, 6.0, true));
            enemies.add(enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-%012d".formatted(index), index + 1));
        }

        DefensePlan plan = DefensePlanner.plan(
                state(), defenders, enemies, ThreatBearingTests.PROFILES, DefenseProfileTests.PROFILE);

        long targeted = plan.orders().stream()
                .filter(order -> order.target().isPresent())
                .count();
        assertEquals(16, targeted, "Цели ограничены пределом обзора: иначе пересборка тяжелеет сверх бюджета тика");
        assertEquals(
                1,
                plan.orders().size() - targeted,
                "Лишний защитник держит пост без цели: иначе дух без цели останется без приказа");
        Set<String> targetedIds = plan.orders().stream()
                .filter(order -> order.target().isPresent())
                .map(order -> order.target().orElseThrow().id())
                .collect(Collectors.toSet());
        assertTrue(
                !targetedIds.contains("aaaaaaaa-aaaa-aaaa-aaaa-000000000016"),
                "Дальний семнадцатый за пределом обзора: иначе обрезка не работает и лимит фиктивен");
    }

    @Test
    void plan_NoEnemies_PostsOnly() {
        DefensePlan plan = DefensePlanner.plan(
                state(),
                List.of(archer(ARCHER_ID, 1.0), melee(MELEE_ID, 10, 1.0)),
                List.of(),
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        assertTrue(
                plan.posts().size() == 2,
                "Без врагов посты всё равно стоят: иначе мирное время оставит периметр пустым");
        assertEquals(List.of(), plan.orders(), "Без врагов приказов нет: иначе дух получит цель из ничего");
    }

    @Test
    void plan_DuplicateDefender_Throws() {
        DefenderCapability defender = melee(MELEE_ID, 10, 1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> DefensePlanner.plan(
                        state(),
                        List.of(defender, defender),
                        List.of(enemy("minecraft:zombie", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 5)),
                        ThreatBearingTests.PROFILES,
                        DefenseProfileTests.PROFILE),
                "Дух дважды в списке обязан падать: один дух — один пост и один приказ");
    }
}

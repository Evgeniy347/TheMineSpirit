package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TargetSelector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefenseDirectivesTests {
    private static final Point CENTER = new Point(0, 64, 0);
    private static final UUID FIRST = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String ZOMBIE_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String ZOMBIE2_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String CREEPER_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    private static DefenderCapability archer(UUID id) {
        return new DefenderCapability(id, new Point(1, 64, 1), 1.0, 10, true, 6.0, true);
    }

    private static DefenderCapability melee(UUID id, double hp) {
        return new DefenderCapability(id, new Point(-1, 64, 1), hp, 15, false, 6.0, false);
    }

    private static EnemyFact enemy(String type, String id, double x) {
        return new EnemyFact(type, id, new Point(x, 64, 0), 1.0, 0, true, true);
    }

    private static CoordinatorState state() {
        return new CoordinatorState(Optional.of(CENTER), 16.0, List.of(), 0);
    }

    private static DefensePlan plan(List<DefenderCapability> defenders, List<EnemyFact> enemies) {
        return DefensePlanner.plan(
                state(), defenders, enemies, ThreatBearingTests.PROFILES, DefenseProfileTests.PROFILE);
    }

    @Test
    void directives_NullPlan_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DefenseDirectives.of(null),
                "Приказ из null-плана не собрать: иначе дух получит приказ из ничего");
    }

    @Test
    void directives_NoDefendedPoint_ReturnsEmpty() {
        CoordinatorState homeless = new CoordinatorState(Optional.empty(), 0, List.of(), 0);
        DefensePlan plan = DefensePlanner.plan(
                homeless,
                List.of(melee(FIRST, 1.0)),
                List.of(enemy("minecraft:zombie", ZOMBIE_ID, 5)),
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        assertEquals(
                Map.of(),
                DefenseDirectives.of(plan),
                "Без точки обороны приказов нет: иначе посты встанут вокруг ничего");
    }

    @Test
    void directives_PostHolder_GuardAreaAtOwnPost() {
        DefensePlan plan = DefensePlanner.plan(
                state(),
                List.of(melee(FIRST, 1.0)),
                List.of(),
                ThreatBearingTests.PROFILES,
                DefenseProfileTests.PROFILE);

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan);

        DefenseIssuedOrder order = issued.get(FIRST);
        assertEquals(
                CombatMode.GUARD_AREA,
                order.directive().mode(),
                "Держатель поста стоит в GUARD_AREA: иначе тактика уведёт его с сектора");
        assertEquals(
                Optional.of(postOf(plan, FIRST)),
                order.directive().safePoint(),
                "Точка приказа — свой пост, а не центр: иначе защитники скучатся в одной точке");
        assertEquals(
                Optional.empty(),
                order.forceTargetId(),
                "Без цели навязывать некого: иначе дух бьёт пустоту вместо дозора");
    }

    @Test
    void directives_EngageTarget_AggressiveWithForcedId() {
        DefensePlan plan = plan(List.of(archer(FIRST)), List.of(enemy("minecraft:creeper", CREEPER_ID, 10)));

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan);

        DefenseIssuedOrder order = issued.get(FIRST);
        assertEquals(
                CombatMode.AGGRESSIVE,
                order.directive().mode(),
                "Приказ с целью — AGGRESSIVE: иначе лучник стоит на посту, а крипер идёт к дому");
        assertEquals(
                Optional.of(new TargetSelector.Target(TargetSelector.Kind.HOSTILE, Optional.empty())),
                order.directive().target(),
                "Цель приказа — враждебный моб: конкретный id едет отдельным полем, селектор его не адресует");
        assertEquals(
                Optional.of(CREEPER_ID),
                order.forceTargetId(),
                "Навязанная цель — выбранный планировщиком крипер: иначе лучник бьёт ближайшего вместо приоритетного");
    }

    @Test
    void directives_ChargedCreeperMelee_WithdrawsInsteadOfAttack() {
        DefensePlan plan = plan(List.of(melee(FIRST, 1.0)), List.of(enemy("minecraft:creeper", CREEPER_ID, 5)));
        assertEquals(
                CombatMode.AGGRESSIVE,
                DefenseDirectives.of(plan).get(FIRST).directive().mode(),
                "Контроль: без метки заряженности мечник идёт в «ударь и беги», иначе нечего фильтровать");

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan, Set.of(CREEPER_ID));

        DefenseIssuedOrder order = issued.get(FIRST);
        assertEquals(
                CombatMode.PASSIVE,
                order.directive().mode(),
                "Заряженного крипера ближним боем не атаковать никогда: мечник отходит, бьёт лучник");
        assertEquals(
                Optional.of(RetreatMethod.HOME),
                order.directive().retreat(),
                "Отход — именованным способом: иначе тактика не знает, куда уводить духа");
        assertEquals(
                Optional.of(CENTER),
                order.directive().safePoint(),
                "Отход — к точке обороны: иначе раненый рубеж останется без прикрытия");
        assertEquals(
                Optional.empty(), order.forceTargetId(), "Отошедший цели не навязывает: иначе дух вернётся под взрыв");
    }

    @Test
    void directives_ChargedCreeperArcher_StillAttacks() {
        DefensePlan plan = plan(List.of(archer(FIRST)), List.of(enemy("minecraft:creeper", CREEPER_ID, 10)));

        CombatDirective directive =
                DefenseDirectives.of(plan, Set.of(CREEPER_ID)).get(FIRST).directive();

        assertEquals(
                CombatMode.AGGRESSIVE,
                directive.mode(),
                "Запрет ближнего боя касается только мечника: лучник расстреливает заряженного с дистанции");
    }

    @Test
    void directives_WoundedHandsOff_IdleAllyTakesTarget() {
        DefensePlan plan =
                plan(List.of(melee(FIRST, 0.1), melee(SECOND, 1.0)), List.of(enemy("minecraft:zombie", ZOMBIE_ID, 5)));
        DefenderOrder wounded = plan.orders().stream()
                .filter(order -> order.defender().equals(FIRST))
                .findFirst()
                .orElseThrow();
        assertEquals(
                CombatStance.WITHDRAW, wounded.stance(), "Раненый без лечилки отходит: иначе некому передавать цель");
        assertEquals(
                Optional.of(SECOND),
                wounded.handOff(),
                "Раненый передаёт цель здоровому: иначе зомби останется без присмотра");

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan);

        assertEquals(
                CombatMode.PASSIVE,
                issued.get(FIRST).directive().mode(),
                "Раненый уходит в отход, а не продолжает бой: иначе он гибнет на месте");
        DefenseIssuedOrder ally = issued.get(SECOND);
        assertEquals(
                CombatMode.AGGRESSIVE,
                ally.directive().mode(),
                "Свободный приёмник принимает бой: иначе переданная цель потеряна");
        assertEquals(
                Optional.of(ZOMBIE_ID),
                ally.forceTargetId(),
                "Приёмник бьёт именно переданного зомби: иначе передача — пустой звук");
    }

    @Test
    void directives_HandoffToBusyTarget_KeepsOwnTarget() {
        DefensePlan plan = plan(
                List.of(melee(FIRST, 0.1), melee(SECOND, 1.0)),
                List.of(enemy("minecraft:zombie", ZOMBIE_ID, 5), enemy("minecraft:zombie", ZOMBIE2_ID, -5)));

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan);

        assertEquals(
                Optional.of(ZOMBIE2_ID),
                issued.get(SECOND).forceTargetId(),
                "Занятый своей целью приёмник её не бросает: иначе передача срывает чужой бой");
    }

    private static Point postOf(DefensePlan plan, UUID defender) {
        return plan.posts().stream()
                .filter(post -> post.defender().equals(defender))
                .findFirst()
                .orElseThrow()
                .post();
    }

    @Test
    void directives_EveryDefender_ExactlyOneOrder() {
        DefensePlan plan =
                plan(List.of(melee(FIRST, 1.0), archer(SECOND)), List.of(enemy("minecraft:zombie", ZOMBIE_ID, 5)));

        Map<UUID, DefenseIssuedOrder> issued = DefenseDirectives.of(plan);

        assertEquals(
                Set.of(FIRST, SECOND),
                issued.keySet(),
                "У каждого защитника ровно один приказ: иначе один стоит без дела, а второй рвётся надвое");
        assertTrue(
                issued.values().stream().allMatch(order -> order.directive() != null),
                "Приказ без директивы не исполнить: тактике нужен явный режим");
    }
}

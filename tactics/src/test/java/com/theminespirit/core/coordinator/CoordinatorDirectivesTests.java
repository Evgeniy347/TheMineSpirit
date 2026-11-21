package com.theminespirit.core.coordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.TargetSelector;
import com.theminespirit.core.tactics.api.Point;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoordinatorDirectivesTests {
    private static final UUID DEFENDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID HELPER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID WARD = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Point CENTER = new Point(0, 64, 0);
    private static final Point POST = new Point(16, 64, 0);

    private static CoordinatorPlan plan(List<DefenseAssignment> defense, List<AssistOrder> assists) {
        return new CoordinatorPlan(Optional.of(CENTER), 16, defense, assists, List.of());
    }

    @Test
    void directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint() {
        CoordinatorPlan plan = plan(List.of(new DefenseAssignment(DEFENDER, POST, 0)), List.of());

        Map<UUID, CombatDirective> directives = CoordinatorDirectives.of(plan);

        CombatDirective directive = directives.get(DEFENDER);
        assertEquals(
                CombatMode.GUARD_AREA,
                directive.mode(),
                "Защитник получает приказ обороны: иначе тактика духа не знает, что держать пост"
                        + " При смене directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint проверь это утверждение.");
        assertEquals(
                Optional.of(POST),
                directive.safePoint(),
                "Пост обороны — точка приказа: без неё защитник отойдёт от сектора"
                        + " Иначе тест directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint перестанет ловить эту поломку. При смене directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                directive.target(),
                "Оборона у поста не назначает цели: цель приказа — дело самой тактики"
                        + " Иначе тест directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint перестанет ловить эту поломку. При смене directives_PostOnPlan_GuardDirectiveWithPostAsSafePoint проверь это утверждение.");
    }

    @Test
    void directives_AssistOrderOnPlan_ProtectDirectiveOnWard() {
        CoordinatorPlan plan =
                plan(List.of(new DefenseAssignment(HELPER, POST, 0)), List.of(new AssistOrder(HELPER, WARD)));

        Map<UUID, CombatDirective> directives = CoordinatorDirectives.of(plan);

        CombatDirective directive = directives.get(HELPER);
        assertEquals(
                CombatMode.PROTECT,
                directive.mode(),
                "Помощник защищает подопечного: приказ обязан выбрать режим защиты, а не оборону поста"
                        + " Иначе тест directives_AssistOrderOnPlan_ProtectDirectiveOnWard перестанет ловить эту поломку. При смене directives_AssistOrderOnPlan_ProtectDirectiveOnWard проверь это утверждение.");
        assertEquals(
                Optional.of(new TargetSelector.Target(TargetSelector.Kind.SPIRIT, Optional.of(WARD.toString()))),
                directive.target(),
                "Подопечный помощника — атакованный дух, иначе помогать некому"
                        + " При смене directives_AssistOrderOnPlan_ProtectDirectiveOnWard проверь это утверждение.");
    }

    @Test
    void directives_AssistOverridesPostDuty_HelperHasSingleDirective() {
        CoordinatorPlan plan =
                plan(List.of(new DefenseAssignment(HELPER, POST, 0)), List.of(new AssistOrder(HELPER, WARD)));

        Map<UUID, CombatDirective> directives = CoordinatorDirectives.of(plan);

        assertEquals(
                1,
                directives.size(),
                "Один дух — один приказ: помощь и оборона поста не накладываются друг на друга"
                        + " Иначе тест directives_AssistOverridesPostDuty_HelperHasSingleDirective перестанет ловить эту поломку. При смене directives_AssistOverridesPostDuty_HelperHasSingleDirective проверь это утверждение.");
        assertEquals(
                CombatMode.PROTECT,
                directives.get(HELPER).mode(),
                "Помощник уходит к подопечному: приказ помощи важнее поста"
                        + " Иначе тест directives_AssistOverridesPostDuty_HelperHasSingleDirective перестанет ловить эту поломку. При смене directives_AssistOverridesPostDuty_HelperHasSingleDirective проверь это утверждение.");
    }

    @Test
    void directives_NoDefendedPoint_NoDirectives() {
        CoordinatorPlan plan = new CoordinatorPlan(
                Optional.empty(), 0, List.of(new DefenseAssignment(DEFENDER, POST, 0)), List.of(), List.of());

        assertTrue(
                CoordinatorDirectives.of(plan).isEmpty(),
                "Без защищаемой точки обороны нет: приказывать держать нечего"
                        + " Иначе тест directives_NoDefendedPoint_NoDirectives перестанет ловить эту поломку. При смене directives_NoDefendedPoint_NoDirectives проверь это утверждение.");
    }

    @Test
    void directives_NullPlan_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CoordinatorDirectives.of(null),
                "Приказ собирается из плана: план обязателен, иначе решение принимается без входа"
                        + " При смене directives_NullPlan_Throws проверь это утверждение.");
    }
}

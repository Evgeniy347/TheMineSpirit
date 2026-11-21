package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Orders;
import com.theminespirit.core.tactics.api.Point;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrdersBuilderTests {
    private static final Point CENTER = new Point(10, 1, 10);

    @ParameterizedTest
    @EnumSource(CombatMode.class)
    void build_EveryMode_KeepsOrderedMode(CombatMode mode) {
        Orders orders = OrdersBuilder.build(mode, Optional.empty(), Optional.empty(), false);

        assertEquals(
                mode,
                orders.mode(),
                "Приказанный режим дойдёт до ядра без подмены: " + mode
                        + " Иначе тест build_EveryMode_KeepsOrderedMode перестанет ловить эту поломку. При смене build_EveryMode_KeepsOrderedMode проверь это утверждение.");
    }

    @Test
    void build_ZoneWithRadius_ZoneSet() {
        Orders orders = OrdersBuilder.build(
                CombatMode.GUARD_AREA, Optional.of(new OrdersBuilder.Zone(CENTER, 8)), Optional.empty(), false);

        assertTrue(
                orders.zoneSet(),
                "Зона с радиусом больше нуля — это зона"
                        + " Иначе тест build_ZoneWithRadius_ZoneSet перестанет ловить эту поломку. При смене build_ZoneWithRadius_ZoneSet проверь это утверждение.");
        assertEquals(
                8.0,
                orders.zoneRadius(),
                "Радиус зоны не меняется"
                        + " Иначе тест build_ZoneWithRadius_ZoneSet перестанет ловить эту поломку. При смене build_ZoneWithRadius_ZoneSet проверь это утверждение.");
        assertEquals(
                CENTER,
                orders.zoneCenter(),
                "Центр зоны не меняется"
                        + " Иначе тест build_ZoneWithRadius_ZoneSet перестанет ловить эту поломку. При смене build_ZoneWithRadius_ZoneSet проверь это утверждение.");
    }

    @Test
    void build_ZoneWithZeroRadius_NoZone() {
        Orders orders = OrdersBuilder.build(
                CombatMode.GUARD_AREA, Optional.of(new OrdersBuilder.Zone(CENTER, 0)), Optional.empty(), false);

        assertFalse(
                orders.zoneSet(),
                "Нулевой радиус — зоны нет: Orders иначе отвергнет центр без радиуса"
                        + " При смене build_ZoneWithZeroRadius_NoZone проверь это утверждение.");
        assertNull(
                orders.zoneCenter(),
                "Без зоны центра нет"
                        + " Иначе тест build_ZoneWithZeroRadius_NoZone перестанет ловить эту поломку. При смене build_ZoneWithZeroRadius_NoZone проверь это утверждение.");
    }

    @Test
    void build_HugeRadius_ClampedToMaxZoneRadius() {
        Orders orders = OrdersBuilder.build(
                CombatMode.GUARD_AREA, Optional.of(new OrdersBuilder.Zone(CENTER, 5000)), Optional.empty(), false);

        assertEquals(
                Orders.MAX_ZONE_RADIUS,
                orders.zoneRadius(),
                "Рабочая зона духа может быть больше предела ядра: радиус срезается, а не роняет тик"
                        + " Иначе тест build_HugeRadius_ClampedToMaxZoneRadius перестанет ловить эту поломку. При смене build_HugeRadius_ClampedToMaxZoneRadius проверь это утверждение.");
    }

    @Test
    void build_WardThreatened_PassesWardAndThreat() {
        Orders orders = OrdersBuilder.build(
                CombatMode.PROTECT, Optional.empty(), Optional.of(new OrdersBuilder.Ward(CENTER, true)), true);

        assertTrue(
                orders.wardPresent() && orders.wardThreatened(),
                "Подопечный под угрозой дойдёт до ядра"
                        + " Иначе тест build_WardThreatened_PassesWardAndThreat перестанет ловить эту поломку. При смене build_WardThreatened_PassesWardAndThreat проверь это утверждение.");
        assertTrue(
                orders.helpAvailable(),
                "Признак помощи дойдёт до ядра"
                        + " Иначе тест build_WardThreatened_PassesWardAndThreat перестанет ловить эту поломку. При смене build_WardThreatened_PassesWardAndThreat проверь это утверждение.");
    }

    @Test
    void build_NullMode_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OrdersBuilder.build(null, Optional.empty(), Optional.empty(), false),
                "Без режима боя приказы бессмысленны: ошибка сборки, а не молчаливый DEFENSIVE"
                        + " Иначе тест build_NullMode_Rejected перестанет ловить эту поломку. При смене build_NullMode_Rejected проверь это утверждение.");
    }
}

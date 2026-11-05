package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockProtectionOwnTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.readClasspath();
    }

    @Test
    void checkOwn_OwnWallInsideOwnBuild_IsAllowed() {
        BlockProtection.Decision refused =
                protection.check("minecraft:cobblestone", BlockProtectionTests.house(), false, false);

        assertInstanceOf(
                BlockProtection.Refused.class,
                refused,
                "Предусловие: стена из булыжника среди 14 искусственных соседей считается постройкой игрока и обычной проверкой запрещена");
        assertEquals(
                new BlockProtection.Allowed(),
                protection.checkOwn("minecraft:cobblestone", false),
                "Свой поставленный блок дух разбирает: эвристика «постройка игрока» и защита игрока на блоки духов не распространяются, "
                        + "иначе дух не разберёт то, что построил");
    }

    @Test
    void checkOwnPlan_OwnTorchInPlan_IsAllowedButOffPlanIsKept() {
        assertEquals(
                new BlockProtection.Allowed(),
                protection.checkOwn("minecraft:wall_torch", false, true),
                "Свой факел шахты дух снимает и переставляет: чёрный список защищает чужие механизмы, "
                        + "а не блоки, которые дух сам поставил в клетке плана (observe7: wall_torch держал 75%)");
        assertInstanceOf(
                BlockProtection.Refused.class,
                protection.checkOwn("minecraft:wall_torch", false, false),
                "Вне плана чёрный список по-прежнему держит свои факелы и механизмы: разрешение не снимает защиту "
                        + "целиком, а действует только на свои постройки плана");
    }

    @Test
    void checkOwn_DenyBlockInZone_IsStillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                protection.checkOwn("minecraft:chest", false),
                "Свой сундук всё равно запрещён: чёрный список действует и на блоки духов, иначе дух разберёт механизм");
        assertInstanceOf(
                BlockProtection.Refused.class,
                protection.checkOwn("minecraft:cobblestone", true),
                "Своя постройка в защищённой зоне всё равно запрещена: зона сильнее метки духа");
    }
}

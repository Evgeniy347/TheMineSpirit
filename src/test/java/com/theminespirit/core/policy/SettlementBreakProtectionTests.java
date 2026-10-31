package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SettlementBreakProtectionTests {
    private BlockProtection protection;

    @BeforeAll
    void loadProtection() {
        protection = ProtectionConfig.load();
    }

    @Test
    void own_SpiritWallTorchOutsidePlan_Allowed() {
        assertEquals(
                new BlockProtection.Allowed(),
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", false),
                "Свой факел духа вне клетки плана (шахта -189,60,-507) обязан ломаться: решение принимает "
                        + "ProtectionGate.checkBreak, а не отдельная проверка с другим флагом плана");
    }

    @Test
    void own_ContainerOutsidePlan_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:chest", false),
                "Свой сундук всё равно нельзя: контейнеры запрещены для gather, иначе дух разберёт склад");
    }

    @Test
    void own_WallTorchInProtectedZone_StillRefused() {
        assertInstanceOf(
                BlockProtection.Refused.class,
                SettlementBreakProtection.own(protection, "minecraft:wall_torch", true),
                "Защищённая зона сильнее метки духа: факел в чужой зоне не ломают");
    }
}

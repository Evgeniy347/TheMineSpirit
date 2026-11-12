package com.theminespirit.neoforge.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class OtherSpiritSupportTests {
    @Test
    void supports_BlockUnderFeet_IsCarrying() {
        BlockPos top = new BlockPos(-176, 72, -509);
        assertTrue(
                OtherSpiritSupport.supports(top, top.above(), top),
                "блок прямо под ногами другого духа — его опора: откат брошенной аренды или расчистка, выбив его,"
                        + " роняют духа со столба (вопрос владельца 12.11: хозяин стоит на столбе дольше 5 мин, аренда"
                        + " брошена). Сломается: смерти от падения. Проверить при смене: UndoLeaseScript, ClearAreaRun");
    }

    @Test
    void supports_BlockLowerInPillar_IsFree() {
        BlockPos top = new BlockPos(-176, 72, -509);
        assertFalse(
                OtherSpiritSupport.supports(top, top.above(), top.below()),
                "нижний блок столба не держит духа напрямую (земля не падает): запрет только на опору под ногами,"
                        + " иначе откат и расчистка встанут на весь столб. Проверить при смене: блоки с гравитацией");
    }
}

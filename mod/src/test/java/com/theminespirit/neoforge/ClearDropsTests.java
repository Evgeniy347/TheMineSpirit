package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearDropsTests {
    @Test
    void continues_InventoryFull_KeepsClearing() {
        assertTrue(
                ClearDrops.continues(true),
                "полный инвентарь не останавливает расчистку: дроп остаётся на земле, а выгрузку ставит граф"
                        + " (inventory_overloaded). Иначе тупик: к сундуку не пройти, пока не расчищен проход, а"
                        + " расчистить нельзя, пока полон инвентарь. Проверить при смене: ClearAreaSession.collectTick");
    }

    @Test
    void continues_OtherPickupFailure_Stops() {
        assertFalse(
                ClearDrops.continues(false),
                "прочие сбои подбора (не дотянулся, таймаут) по-прежнему останавливают расчистку с причиной:"
                        + " молча бросать дроп из-за пути нельзя. Проверить при смене: DropCollector.fail");
    }
}

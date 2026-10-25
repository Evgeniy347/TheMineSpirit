package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SettlementLayoutHouseTests {
    private static final BlockCoord ANCHOR = new BlockCoord(0, 64, 0);

    @Test
    void houseColumn_AnchorAndWorkYard_AnchorInsideYardOutside() {
        assertTrue(
                SettlementLayout.houseColumn(ANCHOR, ANCHOR.x(), ANCHOR.z()),
                "Якорь участка — в габарите хижины: задания «к якорю» после её постройки ведут во двор (сцена 56)");
        BlockCoord yard = SettlementLayout.workYard(ANCHOR);
        assertFalse(
                SettlementLayout.houseColumn(ANCHOR, yard.x(), yard.z()),
                "Рабочий двор — вне габарита хижины: туда уводится goto из стоящей хижины, двери дух не открывает");
    }
}

package com.theminespirit.neoforge.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StockReaderTests {
    @Test
    void stockReadBudget_DeclaredValue_Equals4096() {
        assertEquals(
                4096,
                StockReader.STOCK_READ_BUDGET,
                "Бюджет чтений запасов на пересборку снимка — 4096: больше — тик интеллекта читает полмира, "
                        + "меньше — склад виден не целиком");
    }
}

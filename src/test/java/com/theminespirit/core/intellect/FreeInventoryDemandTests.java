package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.InventoryFill;
import org.junit.jupiter.api.Test;

class FreeInventoryDemandTests {
    private static final String CURVES = """
            {"demand":{"occupied_curve":[{"at":0.0,"value":0.0},{"at":0.5,"value":1.0},{"at":1.0,"value":8.0}],
            "extra_curve":[{"at":0.0,"value":0.0},{"at":1.0,"value":5.0}]},
            "release_free_slots":{"CHOP":0,"FARM":2,"FORAGE":2,"MINE":0,"REST":0,"DEFEND":0,"UNLOAD":0,"SMELT":2,"CRAFT":2}}
            """;

    private static final InventoryNormTable TABLE = InventoryNormTable.parse(CURVES, "тест");

    @Test
    void demand_EmptyInventory_IsZero() {
        assertEquals(0.0, FreeInventoryDemand.demand(InventoryFill.EMPTY, InventoryNormTable.load()), 1e-9,
                "Пустой инвентарь не создаёт спроса: иначе дух бросает работу ради пустой разгрузки");
    }

    @Test
    void demand_PartlyFilled_AddsBothCurves() {
        assertEquals(0.5, FreeInventoryDemand.demand(0.25, 0.0, TABLE), 1e-9,
                "Половина пути по кривой занятости (0..1 на 0..0.5) даёт 0.5");
        assertEquals(3.0, FreeInventoryDemand.demand(0.25, 0.5, TABLE), 1e-9,
                "Спрос — сумма кривой занятости и кривой лишнего, а не максимум");
    }

    @Test
    void demand_FullInventory_ClampsToMax() {
        InventoryFill full = new InventoryFill(36, 36, 200, 200);

        assertEquals(IntellectTuning.MAX_DEMAND, FreeInventoryDemand.demand(full, TABLE), 1e-9,
                "Полный забитый лишним инвентарь упирается в потолок шкалы: иначе распределение сравнивает "
                        + "значения выше MAX_DEMAND");
    }

    @Test
    void demand_FractionOutsideRange_Throws() {
        assertThrows(IllegalArgumentException.class, () -> FreeInventoryDemand.demand(1.2, 0, TABLE),
                "Доля вне 0..1 недопустима: кривую не с чем сравнивать");
        assertThrows(IllegalArgumentException.class, () -> FreeInventoryDemand.demand(0, Double.NaN, TABLE),
                "NaN-доля ломает интерполяцию: спрос обязан быть числом");
    }

    @Test
    void demand_OccupancyGrows_DemandDoesNotFall() {
        double previous = -1;
        for (int occupied = 0; occupied <= 36; occupied++) {
            InventoryFill fill = new InventoryFill(occupied, 36, 0, occupied);
            double demand = FreeInventoryDemand.demand(fill, TABLE);
            assertTrue(demand >= previous - 1e-9,
                    "Спрос не падает с ростом занятости: было " + previous + ", стало " + demand);
            previous = demand;
        }
    }
}

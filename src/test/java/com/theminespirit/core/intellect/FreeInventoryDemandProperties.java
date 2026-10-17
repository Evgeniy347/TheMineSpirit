package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

class FreeInventoryDemandProperties {
    private static final String CURVES = """
            {"demand":{"occupied_curve":[{"at":0.0,"value":0.0},{"at":0.4,"value":2.0},{"at":1.0,"value":7.0}],
            "extra_curve":[{"at":0.0,"value":0.0},{"at":0.6,"value":3.0},{"at":1.0,"value":5.0}]},
            "release_free_slots":{"CHOP":0,"FARM":2,"FORAGE":2,"MINE":0,"REST":0,"DEFEND":0,"UNLOAD":0,"SMELT":2,"CRAFT":2}}
            """;

    private static final InventoryNormTable TABLE = InventoryNormTable.parse(CURVES, "тест");

    @Property
    void demand_FractionsGrow_DemandNeverFalls(@ForAll @DoubleRange(min = 0, max = 1) double occupiedA,
            @ForAll @DoubleRange(min = 0, max = 1) double extraA,
            @ForAll @DoubleRange(min = 0, max = 1) double occupiedB,
            @ForAll @DoubleRange(min = 0, max = 1) double extraB) {
        double lowerOccupied = Math.min(occupiedA, occupiedB);
        double lowerExtra = Math.min(extraA, extraB);
        double upperOccupied = Math.max(occupiedA, occupiedB);
        double upperExtra = Math.max(extraA, extraB);

        double lower = FreeInventoryDemand.demand(lowerOccupied, lowerExtra, TABLE);
        double upper = FreeInventoryDemand.demand(upperOccupied, upperExtra, TABLE);

        assertTrue(upper >= lower - 1e-9,
                "Чем полнее инвентарь и больше доля лишнего, тем не меньше спрос освободить его: "
                        + "нижняя точка (" + lowerOccupied + ", " + lowerExtra + ") = " + lower
                        + ", верхняя (" + upperOccupied + ", " + upperExtra + ") = " + upper);
    }
}

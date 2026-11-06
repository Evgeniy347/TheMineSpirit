package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class DemandChangeTests {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";
    private static final Optional<DemandRules.Need> STAGE =
            Optional.of(new DemandRules.Need(DemandRules.Kind.STAGE, "BUILD_MINE", 1));
    private static final Optional<DemandRules.Need> BEDS =
            Optional.of(new DemandRules.Need(DemandRules.Kind.BEDS, "house", 2));

    @Test
    void twoLevels_AlternatingNeeds_LoggedOnlyOnChange() {
        DemandChange change = new DemandChange();

        assertTrue(change.changed(OVERWORLD, STAGE), "первая нужда мира пишется один раз");
        assertTrue(change.changed(NETHER, BEDS), "у второго загруженного уровня своя первая нужда");

        for (int tick = 0; tick < 240; tick++) {
            assertFalse(
                    change.changed(OVERWORLD, STAGE),
                    "тик " + tick + ": [demand] 490 строк за 20 минут — это повтор одной и той же нужды "
                            + "мира, а не изменение; писать надо только смену");
            assertFalse(
                    change.changed(NETHER, BEDS),
                    "тик " + tick + ": нужда второго уровня не менялась, строка не пишется");
        }
    }

    @Test
    void sameLevel_NeedChanges_LoggedOncePerValue() {
        DemandChange change = new DemandChange();

        assertTrue(change.changed(OVERWORLD, STAGE), "первая нужда пишется");
        assertFalse(change.changed(OVERWORLD, STAGE), "та же нужда не пишется повторно");
        assertTrue(change.changed(OVERWORLD, BEDS), "смена нужды пишется");
        assertFalse(change.changed(OVERWORLD, BEDS), "и снова один раз");
        assertTrue(change.changed(OVERWORLD, Optional.empty()), "закрытие всех нужд — тоже изменение");
        assertFalse(change.changed(OVERWORLD, Optional.empty()), "повтор «нужды закрыты» не пишется");
    }
}

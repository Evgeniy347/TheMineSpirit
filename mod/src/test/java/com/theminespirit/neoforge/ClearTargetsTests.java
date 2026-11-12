package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClearTargetsTests {
    @Test
    void offLimits_ForeignLeaseCell_IsSkipped() {
        assertTrue(
                ClearTargets.offLimits(Optional.of(UUID.randomUUID())),
                "клетка чужой аренды (столб, на котором стоит другой дух) не цель расчистки: снимает её только откат"
                        + " владельца, иначе дух падает со своего столба");
    }

    @Test
    void offLimits_FreeCell_IsTarget() {
        assertFalse(ClearTargets.offLimits(Optional.empty()), "клетка без аренды — обычная цель расчистки");
    }
}

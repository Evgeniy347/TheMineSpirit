// lib: Из Baritone (github.com/cabaletta/baritone, src/test/java/baritone/pathing/calc/openset/OpenSetsTest.java),
// лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("slow")
class NavOpenSetSlowTests {
    @Test
    void heap_LargeSets_KeepAscendingCosts() {
        Random random = new Random(42);
        NavOpenSetTests.assertAscending(5_000, random);
        NavOpenSetTests.assertAscending(10_000, random);
    }
}

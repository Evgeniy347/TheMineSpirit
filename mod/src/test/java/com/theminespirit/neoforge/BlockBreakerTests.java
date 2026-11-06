package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.FarmTuning;
import org.junit.jupiter.api.Test;

class BlockBreakerTests {
    @Test
    void reach_ComparedWithFarmTuningReach_IsNotSmaller() {
        assertTrue(
                FarmTuning.REACH <= BlockBreaker.REACH,
                "Досягаемость фермы " + FarmTuning.REACH + " обязана быть не больше досягаемости ломки "
                        + BlockBreaker.REACH + ": иначе планировщик поставит дух туда, откуда BlockBreaker "
                        + "отвергнет обработку клетки");
    }
}

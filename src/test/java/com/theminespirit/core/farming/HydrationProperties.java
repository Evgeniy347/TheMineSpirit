package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class HydrationProperties {
    @Property(tries = 200)
    void watered_RandomWater_MatchesBruteForceBox(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        Set<BlockCoord> water = new HashSet<>();
        int count = random.nextInt(0, 12);
        for (int i = 0; i < count; i++) {
            water.add(new BlockCoord(random.nextInt(-6, 7), random.nextInt(-2, 4), random.nextInt(-6, 7)));
        }
        BlockCoord ground = new BlockCoord(random.nextInt(-3, 4), 0, random.nextInt(-3, 4));

        assertEquals(HydrationTests.bruteForce(water, ground), Hydration.of(water).watered(ground),
                "watered обязан совпадать с перебором ящика [x−4,x+4]×[y,y+1]×[z−4,z+4]: иначе till вспашет сухую "
                        + "землю или откажет на политой. Вода: " + water + ", земля: " + ground);
    }
}

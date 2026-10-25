package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BreathSearchTests {
    private static CellWorld floodedShaft() {
        CellWorld world = new CellWorld();
        for (int y = -1; y <= 8; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    world.set(x, y, z, Cell.SOLID);
                }
            }
        }
        for (int y = 0; y <= 5; y++) {
            world.set(0, y, 0, Cell.WATER);
        }
        for (int y = 6; y <= 8; y++) {
            world.set(0, y, 0, Cell.PASSABLE);
        }
        return world;
    }

    @Test
    void nearest_FloodedShaft_FindsCellWithHeadAboveWater() {
        assertEquals(
                Optional.of(new BlockCoord(0, 5, 0)),
                BreathSearch.nearest(floodedShaft(), new BlockCoord(0, 0, 0)),
                "из дна затопленной шахты аварийный подъём ведёт вверх до клетки, где голова над водой; прыжок "
                        + "на месте не спасает (INC-19)");
    }

    @Test
    void nearest_SealedWater_ReturnsEmpty() {
        CellWorld world = floodedShaft();
        world.set(0, 6, 0, Cell.SOLID);

        assertEquals(
                Optional.empty(),
                BreathSearch.nearest(world, new BlockCoord(0, 0, 0)),
                "запечатанная вода без воздуха — подниматься некуда, поиск называет это пустотой, а не клеткой");
    }
}

package com.theminespirit.core.town;

import static com.theminespirit.core.town.SettlementScanTests.LEVEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class AccessPathTests {
    private static final String CHEST = "minecraft:chest";
    private static final Predicate<String> KEEP = CHEST::equals;
    private static final BlockCoord HOME = new BlockCoord(0, LEVEL, 0);
    private static final BlockCoord CHEST_AT = new BlockCoord(6, LEVEL, 0);

    private static SettlementScanTests.Fake withChest() {
        SettlementScanTests.Fake world = new SettlementScanTests.Fake();
        world.put(CHEST_AT.x(), LEVEL, CHEST_AT.z(), CHEST);
        return world;
    }

    private static void ring(SettlementScanTests.Fake world, int radius, int height, int gapX) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == radius;
                if (!edge || (gapX != 0 && dx == gapX && dz == 0)) {
                    continue;
                }
                for (int dy = 0; dy < height; dy++) {
                    world.put(CHEST_AT.x() + dx, LEVEL + dy, CHEST_AT.z() + dz, "minecraft:dirt");
                }
            }
        }
    }

    @Test
    void plan_OpenGround_NothingToClear() {
        AccessPath.Plan plan = AccessPath.plan(withChest(), KEEP, HOME, List.of(CHEST_AT));

        assertTrue(plan.clear() && plan.unreachable().isEmpty(), "Ровная земля — проход есть: " + plan);
    }

    @Test
    void plan_ChestInsideDirtMound_CutsTwoHighDoorwayOnly() {
        SettlementScanTests.Fake world = withChest();
        ring(world, 2, 6, 0);

        AccessPath.Plan plan = AccessPath.plan(world, KEEP, HOME, List.of(CHEST_AT));

        assertEquals(
                List.of(new BlockCoord(CHEST_AT.x() - 2, LEVEL, 0), new BlockCoord(CHEST_AT.x() - 2, LEVEL + 1, 0)),
                plan.cut(),
                "Сундук в земляном кольце высотой 6 — срезается проём шириной 1 и высотой 2 на уровне пола, "
                        + "а не холм целиком: " + plan);
        assertTrue(plan.fill().isEmpty() && plan.unreachable().isEmpty(), "Подсыпать нечего: " + plan);
    }

    @Test
    void plan_MoundWithGap_WalksAroundWithoutDigging() {
        SettlementScanTests.Fake world = withChest();
        ring(world, 2, 6, 2);

        AccessPath.Plan plan = AccessPath.plan(world, KEEP, HOME, List.of(CHEST_AT));

        assertTrue(plan.clear(), "В кольце есть проём — проход в обход, ничего не срезается: " + plan);
    }

    @Test
    void plan_TrenchAcrossWay_FillsFloorCell() {
        SettlementScanTests.Fake world = withChest();
        for (int z = -AccessPath.RADIUS; z <= AccessPath.RADIUS; z++) {
            world.hole(3, z);
        }

        AccessPath.Plan plan = AccessPath.plan(world, KEEP, HOME, List.of(CHEST_AT));

        assertEquals(1, plan.fill().size(), "Траншея поперёк пути — подсыпается одна клетка пола: " + plan);
        assertEquals(3, plan.fill().get(0).x(), "Подсыпка — в траншее: " + plan);
    }
}

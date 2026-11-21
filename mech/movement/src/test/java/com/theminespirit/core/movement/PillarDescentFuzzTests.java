package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.PillarDescent;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PillarDescentFuzzTests {
    @Test
    void ownColumns_EveryHeight_StayWithinBody() {
        for (int height = 1; height <= 8; height++) {
            FuzzWorld world = FuzzWorld.flat();
            Set<BlockCoord> own = new HashSet<>();
            for (int y = 1; y <= height; y++) {
                world.set(5, y, 5, FuzzWorld.Cell.DIRT);
                own.add(new BlockCoord(5, y, 5));
            }
            BlockCoord feet = new BlockCoord(5, height + 1, 5);
            int top = height;

            PillarDescent.Plan plan = PillarDescent.plan(world, feet, own::contains, 64)
                    .orElseThrow(() -> new AssertionError(
                            "Дух на своём столбе высотой " + top + " обязан спуститься разбором: " + feet));

            Set<BlockCoord> removed = new HashSet<>();
            for (BlockCoord cell : plan.dig()) {
                assertTrue(
                        own.contains(cell),
                        "Разбирают только свои блоки, а план назвал " + cell
                                + " Иначе тест ownColumns_EveryHeight_StayWithinBody перестанет ловить эту поломку. При смене ownColumns_EveryHeight_StayWithinBody проверь это утверждение.");
                removed.add(cell);
                BlockCoord at = cell;
                assertTrue(
                        BodyMotion.fits(after(world, removed), at),
                        "После снятия " + cell + " тело обязано помещаться в " + at
                                + " Иначе тест ownColumns_EveryHeight_StayWithinBody перестанет ловить эту поломку. При смене ownColumns_EveryHeight_StayWithinBody проверь это утверждение.");
            }
            assertEquals(
                    new BlockCoord(5, 1, 5),
                    plan.landing(),
                    "Спуск с высоты " + top + " обязан кончиться на земле, а не посередине столба"
                            + " Иначе тест ownColumns_EveryHeight_StayWithinBody перестанет ловить эту поломку. При смене ownColumns_EveryHeight_StayWithinBody проверь это утверждение.");
            assertTrue(
                    BodyMotion.standable(after(world, removed), plan.landing()),
                    "Спуск обязан кончаться на твёрдой опоре"
                            + " Иначе тест ownColumns_EveryHeight_StayWithinBody перестанет ловить эту поломку. При смене ownColumns_EveryHeight_StayWithinBody проверь это утверждение.");
        }
    }

    @Test
    void bridgeWithoutColumn_NoColumn_NoDescent() {
        FuzzWorld world = FuzzWorld.flat();
        for (int y = -6; y <= 0; y++) {
            for (int x = 0; x < FuzzWorld.SIZE; x++) {
                for (int z = 0; z < FuzzWorld.SIZE; z++) {
                    world.set(x, y, z, FuzzWorld.Cell.AIR);
                }
            }
        }
        Set<BlockCoord> own = new HashSet<>();
        for (int x = 2; x <= 8; x++) {
            world.set(x, 1, 5, FuzzWorld.Cell.DIRT);
            own.add(new BlockCoord(x, 1, 5));
        }
        BlockCoord feet = new BlockCoord(5, 2, 5);

        assertTrue(
                PillarDescent.plan(world, feet, own::contains, 64).isEmpty(),
                "Мост — не столб: под ним провал. Спуск разбором не выдумывается, мост убирает уборка следа"
                        + " Иначе тест bridgeWithoutColumn_NoColumn_NoDescent перестанет ловить эту поломку. При смене bridgeWithoutColumn_NoColumn_NoDescent проверь это утверждение.");
    }

    @Test
    void pillarDescentCases_LiveBridge_NoDescentColumn() throws IOException {
        LiveWorld world = LiveWorld.load("/live/pillarfix-bridge-1109.live");

        assertTrue(
                PillarDescent.plan(world, new BlockCoord(0, 0, 0), cell -> true, 64)
                        .isEmpty(),
                "Живой мост-Г: под духом мост, а под мостом провал. Разбор под собой оставил бы духа падать, "
                        + "поэтому спуск не выдумывается: уборка следа идёт заданием графа"
                        + " Иначе тест pillarDescentCases_LiveBridge_NoDescentColumn перестанет ловить эту поломку. При смене pillarDescentCases_LiveBridge_NoDescentColumn проверь это утверждение.");
    }

    private static RouteWorld after(FuzzWorld world, Set<BlockCoord> removed) {
        return (x, y, z) -> removed.contains(new BlockCoord(x, y, z)) ? RouteCell.AIR : world.cell(x, y, z);
    }
}

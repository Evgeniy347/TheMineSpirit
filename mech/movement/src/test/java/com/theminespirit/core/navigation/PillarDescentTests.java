package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.BodyMotion;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PillarDescentTests {
    private static final int GROUND = 64;

    @Test
    void plan_OwnPillarOfEveryHeight_DigsTopDownToGround() {
        for (int height = 1; height <= 12; height++) {
            Column world = new Column(height, height);
            BlockCoord feet = new BlockCoord(0, GROUND + height + 1, 0);
            int top = feet.y();

            PillarDescent.Plan plan = PillarDescent.plan(world, feet, world::own, 32)
                    .orElseThrow(() -> new AssertionError("Дух стоит на своём столбе высотой " + top
                            + " и обязан спуститься разбором, " + "а планировщик не нашёл спуска: " + feet));

            assertEquals(height, plan.depth(), "Спуск разбирает весь свой столб, по блоку за шаг");
            assertEquals(new BlockCoord(0, GROUND + 1, 0), plan.landing(), "Спуск кончается на земле");
            List<BlockCoord> expected = new ArrayList<>();
            for (int step = 0; step < Math.min(2, height); step++) {
                expected.add(new BlockCoord(0, GROUND + height - step, 0));
            }
            assertEquals(
                    expected,
                    plan.dig().subList(0, Math.min(2, height)),
                    "Столб разбирают сверху вниз: иначе дух повиснет над пустотой");

            Set<BlockCoord> removed = new HashSet<>();
            for (BlockCoord cell : plan.dig()) {
                removed.add(cell);
                BlockCoord at = cell;
                assertTrue(
                        BodyMotion.fits(world.after(removed), at),
                        "После снятия " + cell + " тело обязано помещаться в " + at
                                + ": иначе дух падает сквозь собственный столб");
            }
            assertTrue(
                    BodyMotion.standable(world.after(removed), plan.landing()),
                    "Спуск обязан кончаться на твёрдой опоре, а не в воздухе");
        }
    }

    @Test
    void plan_MixedColumn_StopsAtForeignBlock() {
        Column world = new Column(6, 2);
        BlockCoord feet = new BlockCoord(0, GROUND + 7, 0);

        PillarDescent.Plan plan =
                PillarDescent.plan(world, feet, world::own, 32).orElseThrow();

        assertEquals(2, plan.depth(), "Разбирают только свои блоки: чужие/природные не трогают");
        assertEquals(new BlockCoord(0, GROUND + 5, 0), plan.landing(), "Спуск кончается на первом чужом блоке");
    }

    @Test
    void plan_ForeignPillar_NoPlan() {
        Column world = new Column(4, 0);
        BlockCoord feet = new BlockCoord(0, GROUND + 5, 0);

        assertTrue(
                PillarDescent.plan(world, feet, world::own, 32).isEmpty(),
                "Чужой столб не разбирают: свой след убирает владелец, чужой — отдельное решение");
    }

    @Test
    void plan_OwnBlocksOverVoid_NoPlan() {
        RouteWorld voidPillar =
                (x, y, z) -> x == 0 && z == 0 && y > GROUND && y <= GROUND + 4 ? RouteCell.solid(8) : RouteCell.AIR;
        Set<BlockCoord> own = new HashSet<>();
        for (int y = GROUND + 1; y <= GROUND + 4; y++) {
            own.add(new BlockCoord(0, y, 0));
        }

        assertTrue(
                PillarDescent.plan(voidPillar, new BlockCoord(0, GROUND + 5, 0), own::contains, 32)
                        .isEmpty(),
                "Под своим столбом бездна: разбор оставил бы духа падать глубже безопасного, такой спуск не план");
    }

    @Test
    void plan_DepthLimit_StopsAtAllowedStep() {
        Column world = new Column(12, 12);
        BlockCoord feet = new BlockCoord(0, GROUND + 13, 0);

        PillarDescent.Plan plan = PillarDescent.plan(world, feet, world::own, 5).orElseThrow();

        assertEquals(5, plan.depth(), "Предел спуска ограничивает разбор, а не отменяет его целиком");
        assertEquals(new BlockCoord(0, GROUND + 8, 0), plan.landing(), "Останавливается на своём же блоке ниже");
    }

    @Test
    void plan_OnBridgeWithoutColumn_NoPlan() {
        RouteWorld bridge = (x, y, z) -> y < GROUND - 4
                ? RouteCell.solid(8)
                : (y == GROUND && Math.abs(x) <= 8 && z == 0 ? RouteCell.solid(8) : RouteCell.AIR);
        Set<BlockCoord> own = new HashSet<>();
        for (int x = -8; x <= 8; x++) {
            own.add(new BlockCoord(x, GROUND, 0));
        }

        assertFalse(
                PillarDescent.plan(bridge, new BlockCoord(0, GROUND + 1, 0), own::contains, 32)
                        .isPresent(),
                "Мост — не столб: под ним воздух. Спуск разбором не выдумывается, мост убирает уборка следа");
    }

    private static final class Column implements RouteWorld {
        private final int height;
        private final int own;
        private final int bottom;

        private Column(int height, int own) {
            this(height, own, GROUND);
        }

        private Column(int height, int own, int bottom) {
            this.height = height;
            this.own = own;
            this.bottom = bottom;
        }

        boolean own(BlockCoord cell) {
            return cell.x() == 0 && cell.z() == 0 && cell.y() > bottom + height - own && cell.y() <= bottom + height;
        }

        RouteWorld after(Set<BlockCoord> removed) {
            return (x, y, z) -> {
                BlockCoord cell = new BlockCoord(x, y, z);
                if (removed.contains(cell)) {
                    return RouteCell.AIR;
                }
                return cell(x, y, z);
            };
        }

        @Override
        public int cell(int x, int y, int z) {
            if (x == 0 && z == 0 && y > bottom && y <= bottom + height) {
                return RouteCell.solid(8);
            }
            if (y <= bottom) {
                return RouteCell.solid(8);
            }
            return RouteCell.AIR;
        }
    }
}

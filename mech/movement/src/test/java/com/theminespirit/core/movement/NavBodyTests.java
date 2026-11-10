package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.navigation.NavPillarEscape;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class NavBodyTests {
    @Test
    void bodyMotion_StepUpWithoutHeadroom_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 1, 5);
        world.set(5, 3, 5, FuzzWorld.Cell.STONE);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 2, 5)),
                "Шаг на ступень под притолокой неисполним: тело ростом 1.4 поднимает голову в клетку над головой, "
                        + "а она занята. Планировщик, разрешивший такой подъём, оставит духа прыгать на месте");
    }

    @Test
    void bodyMotion_DropUnderLintel_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 4, 5);
        world.set(5, 3, 5, FuzzWorld.Cell.DIRT);
        world.set(6, 4, 5, FuzzWorld.Cell.STONE);
        world.set(6, 3, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.AIR);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "Спуск под притолоку неисполним: в целевой колонке занята клетка на исходной высоте, и голова "
                        + "духа не проходит. Путь «к небу» вниз под притолоку — та же ошибка");
    }

    @Test
    void bodyMotion_DropFourBlocksWithoutWater_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 6, 5);
        world.set(5, 5, 5, FuzzWorld.Cell.AIR);
        world.set(5, 4, 5, FuzzWorld.Cell.AIR);
        world.set(5, 3, 5, FuzzWorld.Cell.AIR);
        world.set(5, 2, 5, FuzzWorld.Cell.AIR);

        assertFalse(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "Падение глубже трёх блоков на сушу калечит духа: тело не считает такой спуск безопасным");
    }

    @Test
    void bodyMotion_DropFourBlocksIntoWater_Allowed() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 6, 5);
        for (int y = 1; y <= 5; y++) {
            world.set(5, y, 5, FuzzWorld.Cell.AIR);
            world.set(6, y, 5, FuzzWorld.Cell.WATER);
        }

        assertTrue(
                BodyMotion.canMove(world, feet, new BlockCoord(6, 1, 5)),
                "В воду падать можно глубже трёх блоков: вода гасит удар, и дух выбирается на берег");
    }

    @Test
    void bodyMotion_OneHighSlit_NotFit() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord slit = new BlockCoord(6, 1, 5);
        world.set(6, 1, 5, FuzzWorld.Cell.AIR);
        world.set(6, 2, 5, FuzzWorld.Cell.STONE);

        assertFalse(
                BodyMotion.fits(world, slit),
                "Щель высотой 1 не проходит для тела ростом 1.4: планировщик, считающий её выходом, снова "
                        + "оставит духа замурованным");
    }

    @Test
    void bodyMotion_Ladder_ClimbsUpWithoutSupport() {
        FuzzWorld world = FuzzWorld.flat();
        BlockCoord feet = new BlockCoord(5, 1, 5);
        world.set(5, 1, 5, FuzzWorld.Cell.LADDER);
        world.set(5, 2, 5, FuzzWorld.Cell.LADDER);
        world.set(5, 3, 5, FuzzWorld.Cell.AIR);

        assertTrue(
                BodyMotion.canMove(world, feet, new BlockCoord(5, 2, 5)),
                "По лестнице дух поднимается без опоры под ногами: лестница — проходимая клетка");
    }

    @Test
    void navPillarEscape_LandingUnderLintel_Rejected() {
        FuzzWorld world = FuzzWorld.flat();
        for (int y = 1; y <= 3; y++) {
            world.set(5, y, 5, FuzzWorld.Cell.DIRT);
        }
        for (int[] side : FuzzWorld.SIDES) {
            world.set(5 + side[0], 4, 5 + side[1], FuzzWorld.Cell.STONE);
        }
        Set<BlockCoord> plateau = NavPillarEscape.plateau(FuzzWorldViews.navWorld(world), 5, 4, 5);
        assertEquals(1, plateau.size(), "Предусловие: дух стоит на одиночном столбе, а не на ровном полу");

        assertTrue(
                NavPillarEscape.landingFrom(FuzzWorldViews.navWorld(world), plateau, 6)
                        .isEmpty(),
                "Спуск под притолоку неисполним: соседняя колонка занята на высоте ног, и тело туда не пройдёт. "
                        + "Планировщик, назвавший такой спуск, отправляет духа вниз сквозь блок");
    }

    @Test
    void navPillarEscape_LiveFixtures_LandingReachableByBody() throws IOException {
        LiveWorld pillar = LiveWorld.load("/live/observe25-pillar-1108.live");
        Set<BlockCoord> single = NavPillarEscape.plateau(pillar, -155, 72, -490);
        assertEquals(1, single.size(), "Живая клетка spirit-1 (-155,72,-490) — одиночная опора");
        int ground = NavPillarEscape.landingFrom(pillar, single, 12).orElse(-1);
        assertEquals(68, ground, "Под одиночной опорой spirit-1 площадка y=68");
        assertTrue(
                NavEscapeChecks.canDrop(pillar, single, ground, 12),
                "Тело духа обязано пройти с живой опоры (-155,72,-490) на площадку y=68: иначе модель проходимости "
                        + "расходится с observe25");

        LiveWorld tower = LiveWorld.load("/live/observe26-tower-1108.live");
        Set<BlockCoord> two = NavPillarEscape.plateau(tower, -142, 79, -504);
        assertEquals(2, two.size(), "Опора spirit-4 шириной 2×2 — всё ещё узкая опора");
        int yard = NavPillarEscape.landingFrom(tower, two, 12).orElse(-1);
        assertEquals(72, yard, "Предел спуска с башни spirit-4 — земля y=72");
        assertTrue(
                NavEscapeChecks.canDrop(tower, two, yard, 12),
                "Тело духа обязано сойти с башни spirit-4 на землю y=72: иначе модель не подтверждает observe26");
    }
}

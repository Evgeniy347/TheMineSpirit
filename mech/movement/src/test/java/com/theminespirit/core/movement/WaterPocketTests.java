package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class WaterPocketTests {
    private static final BlockCoord FEET = new BlockCoord(5, 1, 5);
    private static final BlockCoord HEAD = new BlockCoord(5, 2, 5);

    @Test
    void blocking_WaterUnderRoof_NamesHeadCell() {
        FuzzWorld world = FuzzWorld.flat();
        world.set(FEET.x(), FEET.y(), FEET.z(), FuzzWorld.Cell.WATER);
        world.set(HEAD.x(), HEAD.y(), HEAD.z(), FuzzWorld.Cell.STONE);
        world.set(5, 3, 5, FuzzWorld.Cell.STONE);

        assertFalse(
                BodyMotion.fits(world, FEET), "Тело в воде под твёрдой крышей не помещается: голова занята породой");
        assertEquals(
                List.of(HEAD),
                BodyMotion.blocking(world, FEET),
                "Мешающая клетка тела — голова, а не вода в ногах: ломать надо её, иначе дух не встанет");
    }

    @Test
    void blocking_WaterWithHeadroom_IsEmptyAndFits() {
        FuzzWorld world = FuzzWorld.flat();
        world.set(FEET.x(), FEET.y(), FEET.z(), FuzzWorld.Cell.WATER);
        world.set(HEAD.x(), HEAD.y(), HEAD.z(), FuzzWorld.Cell.WATER);

        assertTrue(BodyMotion.fits(world, FEET), "Вода в ногах и голове — тело помещается");
        assertTrue(
                BodyMotion.blocking(world, FEET).isEmpty(),
                "Вода не мешает телу: правило общее, отдельной ветки «если вода» нет");
    }

    @Test
    void steps_WaterColumn_RisesThroughEveryWaterCell() {
        FuzzWorld world = FuzzWorld.flat();
        for (int y = 1; y <= 4; y++) {
            world.set(5, y, 5, FuzzWorld.Cell.WATER);
        }
        world.set(5, 0, 5, FuzzWorld.Cell.DIRT);

        List<BlockCoord> steps = BodyMotion.steps(world, FEET);

        assertTrue(
                steps.contains(new BlockCoord(5, 2, 5)),
                "По водяному столбу дух всплывает вверх: шаг в +1 — часть общего движения по воде; шаги " + steps);
        assertTrue(BodyMotion.canMove(world, FEET, new BlockCoord(5, 2, 5)), "Всплытие в воде исполнимо телом");
    }

    @Test
    void blocking_StoneAtFeetAndWaterAbove_NamesFeet() {
        FuzzWorld world = FuzzWorld.flat();
        world.set(FEET.x(), FEET.y(), FEET.z(), FuzzWorld.Cell.STONE);
        world.set(HEAD.x(), HEAD.y(), HEAD.z(), FuzzWorld.Cell.WATER);

        assertEquals(
                List.of(FEET),
                BodyMotion.blocking(world, FEET),
                "Если порода в ногах, а вода в голове — ломать надо клетку ног");
    }

    @Test
    void blocking_LiveWaterPocket_NamesHeadFromRealWorld() throws Exception {
        LiveWorld world = LiveWorld.load("/live/water-pocket-1011.live");
        BlockCoord feet = new BlockCoord(0, 0, 0);

        assertFalse(
                BodyMotion.fits(world, feet), "Живой случай 10.11: ноги в воде, голова в породе — тело не помещается");
        assertEquals(
                List.of(new BlockCoord(0, 1, 0)),
                BodyMotion.blocking(world, feet),
                "Из настоящего среза видно ту же клетку головы, что и в модели: вода не поднимает тело сквозь породу");
    }
}

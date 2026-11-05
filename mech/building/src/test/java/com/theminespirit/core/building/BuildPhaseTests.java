package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildPhaseTests {
    private record Cell(String block, int y) {}

    private static List<Cell> current(List<Cell> cells) {
        return BuildPhase.current(cells, Cell::y, cell -> BuildPhase.rough(cell.block()));
    }

    @Test
    void rough_WallsFloorsStairsRoofWindows_AreRough() {
        for (String block : List.of(
                "minecraft:stone_bricks",
                "minecraft:dark_oak_log",
                "minecraft:spruce_planks",
                "minecraft:dark_oak_stairs",
                "minecraft:deepslate_tile_slab",
                "minecraft:glass_pane",
                "minecraft:stone_brick_wall",
                "minecraft:dark_oak_fence")) {
            assertTrue(
                    BuildPhase.rough(block),
                    "'" + block
                            + "' — черновая работа (пол, стены, лестница, крыша, окна, перила): без неё нет этажа и "
                            + "лестницы, по которой дух поднимается выше");
        }
    }

    @Test
    void rough_FurnitureLightDoorsAndShutters_AreFinish() {
        for (String block : List.of(
                "minecraft:red_carpet",
                "minecraft:lantern",
                "minecraft:bookshelf",
                "minecraft:barrel",
                "minecraft:chest",
                "minecraft:crafting_table",
                "minecraft:red_bed",
                "minecraft:dark_oak_door",
                "minecraft:dark_oak_trapdoor",
                "minecraft:potted_poppy",
                "minecraft:wall_torch",
                "minecraft:water")) {
            assertFalse(
                    BuildPhase.rough(block),
                    "'" + block + "' — отделка: ставится после всех черновых работ в любом порядке, иначе дух "
                            + "уходит на верхний этаж за мебелью раньше, чем построена лестница");
        }
    }

    @Test
    void current_RoughLeftInLowBand_OnlyThatBandNow() {
        Cell floor = new Cell("minecraft:stone_bricks", 0);
        Cell wall = new Cell("minecraft:stone_bricks", 3);
        Cell upperWall = new Cell("minecraft:stone_bricks", 4);
        Cell carpet = new Cell("minecraft:red_carpet", 1);

        assertEquals(
                List.of(wall),
                current(List.of(upperWall, carpet, wall)),
                "Пока в нижней полосе из 4 слоёв есть черновые клетки, ставятся только они: в следующую полосу дух "
                        + "не поднимается, отделка ждёт конца черновых работ (владелец 20.10)");
        assertEquals(
                List.of(floor),
                current(List.of(upperWall, carpet, floor, wall)),
                "Пол — раньше стен нижней полосы: отложенная клетка пола иначе остаётся в чулане за стенами");
    }

    @Test
    void current_LowBandDone_NextBandNow() {
        Cell upperWall = new Cell("minecraft:stone_bricks", 5);
        Cell roof = new Cell("minecraft:deepslate_tile_stairs", 9);
        Cell lantern = new Cell("minecraft:lantern", 6);

        assertEquals(
                List.of(upperWall),
                current(List.of(roof, lantern, upperWall)),
                "Нижняя полоса застроена — очередь следующих 4 слоёв, а не крыши и не отделки");
    }

    @Test
    void current_NoRoughLeft_AllFinishInAnyOrder() {
        Cell lantern = new Cell("minecraft:lantern", 9);
        Cell door = new Cell("minecraft:dark_oak_door", 1);
        Cell carpet = new Cell("minecraft:red_carpet", 5);

        assertEquals(
                List.of(lantern, door, carpet),
                current(List.of(lantern, door, carpet)),
                "Черновые работы закончены — вся отделка доступна сразу, в любом порядке");
    }
}

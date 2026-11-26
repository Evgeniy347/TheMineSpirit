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
                            + "лестницы, по которой дух поднимается выше"
                            + " Иначе тест rough_WallsFloorsStairsRoofWindows_AreRough перестанет ловить эту поломку. При смене rough_WallsFloorsStairsRoofWindows_AreRough проверь это утверждение.");
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
                "minecraft:water",
                "minecraft:wheat")) {
            assertFalse(
                    BuildPhase.rough(block),
                    "'" + block + "' — отделка: ставится после всех черновых работ в любом порядке, иначе дух "
                            + "уходит на верхний этаж за мебелью раньше, чем построена лестница"
                            + " При смене rough_FurnitureLightDoorsAndShutters_AreFinish проверь это утверждение.");
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
                        + "не поднимается, отделка ждёт конца черновых работ (владелец 20.10)"
                        + " Иначе тест current_RoughLeftInLowBand_OnlyThatBandNow перестанет ловить эту поломку. При смене current_RoughLeftInLowBand_OnlyThatBandNow проверь это утверждение.");
        assertEquals(
                List.of(floor),
                current(List.of(upperWall, carpet, floor, wall)),
                "Пол — раньше стен нижней полосы: отложенная клетка пола иначе остаётся в чулане за стенами"
                        + " При смене current_RoughLeftInLowBand_OnlyThatBandNow проверь это утверждение.");
    }

    @Test
    void current_LowBandDone_NextBandNow() {
        Cell upperWall = new Cell("minecraft:stone_bricks", 5);
        Cell roof = new Cell("minecraft:deepslate_tile_stairs", 9);
        Cell lantern = new Cell("minecraft:lantern", 6);

        assertEquals(
                List.of(upperWall),
                current(List.of(roof, lantern, upperWall)),
                "Нижняя полоса застроена — очередь следующих 4 слоёв, а не крыши и не отделки"
                        + " Иначе тест current_LowBandDone_NextBandNow перестанет ловить эту поломку. При смене current_LowBandDone_NextBandNow проверь это утверждение.");
    }

    @Test
    void current_FinishOrder_WaterThenLightThenSeeds() {
        Cell earth = new Cell("minecraft:farmland", 0);
        Cell water = new Cell("minecraft:water", 0);
        Cell torch = new Cell("minecraft:torch", 1);
        Cell wheat = new Cell("minecraft:wheat", 1);
        List<Cell> cells = List.of(wheat, torch, water, earth);

        assertEquals(
                List.of(earth),
                BuildPhase.current(
                        cells,
                        Cell::y,
                        cell -> BuildPhase.rough(cell.block()),
                        cell -> SchematicActions.finishOrder(cell.block())),
                "Пока осталась земля, вода и посев ждут: иначе источник оказывается в воздухе и разливается "
                        + "ниже грядки. При смене current_FinishOrder_WaterThenLightThenSeeds проверь "
                        + "SchematicWorkPhase и wheat_field_1.");
        assertEquals(
                List.of(water),
                BuildPhase.current(
                        List.of(wheat, torch, water),
                        Cell::y,
                        cell -> BuildPhase.rough(cell.block()),
                        cell -> SchematicActions.finishOrder(cell.block())),
                "После земли вода ставится первой: иначе источник оказывается в воздухе и разливается ниже поля. "
                        + "При смене current_FinishOrder_WaterThenLightThenSeeds проверь SchematicWorkPhase и wheat_field_1.");
        assertEquals(
                List.of(torch),
                BuildPhase.current(
                        List.of(wheat, torch),
                        Cell::y,
                        cell -> BuildPhase.rough(cell.block()),
                        cell -> SchematicActions.finishOrder(cell.block())),
                "Факел ставится до семян: иначе ночью новый посев может выпасть без освещения. "
                        + "При смене current_FinishOrder_WaterThenLightThenSeeds проверь порядок отделки поля.");
        assertEquals(
                List.of(wheat),
                BuildPhase.current(
                        List.of(wheat),
                        Cell::y,
                        cell -> BuildPhase.rough(cell.block()),
                        cell -> SchematicActions.finishOrder(cell.block())),
                "После воды и факела остаются семена: иначе стройка объявляет поле готовым до посева. "
                        + "При смене current_FinishOrder_WaterThenLightThenSeeds проверь мягкие клетки wheat.");
    }

    @Test
    void current_NoRoughLeft_AllFinishInAnyOrder() {
        Cell lantern = new Cell("minecraft:lantern", 9);
        Cell door = new Cell("minecraft:dark_oak_door", 1);
        Cell carpet = new Cell("minecraft:red_carpet", 5);

        assertEquals(
                List.of(lantern, door, carpet),
                current(List.of(lantern, door, carpet)),
                "Черновые работы закончены — вся отделка доступна сразу, в любом порядке"
                        + " Иначе тест current_NoRoughLeft_AllFinishInAnyOrder перестанет ловить эту поломку. При смене current_NoRoughLeft_AllFinishInAnyOrder проверь это утверждение.");
    }
}

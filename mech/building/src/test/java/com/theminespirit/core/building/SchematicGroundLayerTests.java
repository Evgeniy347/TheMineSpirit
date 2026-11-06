package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import org.junit.jupiter.api.Test;

class SchematicGroundLayerTests {

    @Test
    void wheatField_BottomLayerIsGround_PlacedOnEarth() {
        assertTrue(
                SchematicGround.groundLayer(schematic("wheat_field_1")),
                "У грядки нижний слой — пашня, тропа и вода: они заменяют землю, а не стоят на ней. "
                        + "Без признака грядка ставится в воздух, и тяпке нечего пахать");
    }

    @Test
    void house_BottomLayerIsFloor_StandsOnPlotLevel() {
        assertFalse(
                SchematicGround.groundLayer(schematic("stone_house")),
                "У дома нижний слой — пол: он стоит на уровне площадки, сдвигать его вниз нельзя");
    }

    @Test
    void orchard_BottomLayerHasPath_PathLiesInGroundObjectsOnIt() {
        assertTrue(
                SchematicGround.groundLayer(schematic("orchard_1")),
                "У сада на нижнем слое тропа: она заменяет верхний блок земли, а факелы и саженцы остаются "
                        + "на поверхности. Без признака тропа встаёт блоком над землёй");
    }

    @Test
    void streetDirt_BottomLayerIsGround_TileLiesOnEarth() {
        assertTrue(
                SchematicGround.groundLayer(schematic("street5_dirt")),
                "Плитка улицы этапа 1 — слой земли: он заменяет верхний блок, а не стоит над ним");
    }

    @Test
    void well_BottomLayerHasCobblestone_IsStructureNotGround() {
        assertFalse(
                SchematicGround.groundLayer(schematic("well")),
                "У колодца вода стоит внутри каменного кольца: сдвиг всего низа вниз утопил бы её в землю");
    }

    @Test
    void wheatField_BottomLayerHasFarmland_NeedsHoe() {
        assertTrue(
                SchematicGround.tilled(schematic("wheat_field_1")),
                "Пашню делает тяпка: без признака задание грядки не требует тяпку и стоит, пока её нет");
    }

    @Test
    void orchard_BottomLayerHasNoFarmland_DoesNotNeedHoe() {
        assertFalse(
                SchematicGround.tilled(schematic("orchard_1")),
                "Саду нужна лопата для тропы, а не тяпка: лишнее требование инструмента держало бы постройку");
    }

    private static Schematic schematic(String id) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(id)
                .orElseThrow(() -> new AssertionError("Нет пресета " + id))
                .schematic();
    }
}

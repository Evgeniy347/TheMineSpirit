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
                        + "Без признака грядка ставится в воздух, и тяпке нечего пахать"
                        + " Иначе тест wheatField_BottomLayerIsGround_PlacedOnEarth перестанет ловить эту поломку. При смене wheatField_BottomLayerIsGround_PlacedOnEarth проверь это утверждение.");
    }

    @Test
    void house_BottomLayerIsFloor_StandsOnPlotLevel() {
        assertFalse(
                SchematicGround.groundLayer(schematic("stone_house")),
                "У дома нижний слой — пол: он стоит на уровне площадки, сдвигать его вниз нельзя"
                        + " Иначе тест house_BottomLayerIsFloor_StandsOnPlotLevel перестанет ловить эту поломку. При смене house_BottomLayerIsFloor_StandsOnPlotLevel проверь это утверждение.");
    }

    @Test
    void orchard_BottomLayerHasPath_PathLiesInGroundObjectsOnIt() {
        assertTrue(
                SchematicGround.groundLayer(schematic("orchard_1")),
                "У сада на нижнем слое тропа: она заменяет верхний блок земли, а факелы и саженцы остаются "
                        + "на поверхности. Без признака тропа встаёт блоком над землёй"
                        + " Иначе тест orchard_BottomLayerHasPath_PathLiesInGroundObjectsOnIt перестанет ловить эту поломку. При смене orchard_BottomLayerHasPath_PathLiesInGroundObjectsOnIt проверь это утверждение.");
    }

    @Test
    void streetDirt_BottomLayerIsGround_TileLiesOnEarth() {
        assertTrue(
                SchematicGround.groundLayer(schematic("street5_dirt")),
                "Плитка улицы этапа 1 — слой земли: он заменяет верхний блок, а не стоит над ним"
                        + " Иначе тест streetDirt_BottomLayerIsGround_TileLiesOnEarth перестанет ловить эту поломку. При смене streetDirt_BottomLayerIsGround_TileLiesOnEarth проверь это утверждение.");
    }

    @Test
    void well_BottomLayerHasCobblestone_IsStructureNotGround() {
        assertFalse(
                SchematicGround.groundLayer(schematic("well")),
                "У колодца вода стоит внутри каменного кольца: сдвиг всего низа вниз утопил бы её в землю"
                        + " Иначе тест well_BottomLayerHasCobblestone_IsStructureNotGround перестанет ловить эту поломку. При смене well_BottomLayerHasCobblestone_IsStructureNotGround проверь это утверждение.");
    }

    @Test
    void wheatField_BottomLayerHasFarmland_NeedsHoe() {
        assertTrue(
                SchematicGround.tilled(schematic("wheat_field_1")),
                "Пашню делает тяпка: без признака задание грядки не требует тяпку и стоит, пока её нет"
                        + " Иначе тест wheatField_BottomLayerHasFarmland_NeedsHoe перестанет ловить эту поломку. При смене wheatField_BottomLayerHasFarmland_NeedsHoe проверь это утверждение.");
    }

    @Test
    void orchard_BottomLayerHasNoFarmland_DoesNotNeedHoe() {
        assertFalse(
                SchematicGround.tilled(schematic("orchard_1")),
                "Саду нужна лопата для тропы, а не тяпка: лишнее требование инструмента держало бы постройку"
                        + " Иначе тест orchard_BottomLayerHasNoFarmland_DoesNotNeedHoe перестанет ловить эту поломку. При смене orchard_BottomLayerHasNoFarmland_DoesNotNeedHoe проверь это утверждение.");
    }

    @Test
    void wheatFieldAndOrchard_HaveWalkway_NeedShovel() {
        assertTrue(
                SchematicGround.walkway(schematic("wheat_field_1")),
                "У грядки есть тропа dirt_path: без признака билет не потребует лопату и стройка"
                        + " будет падать на первом же блоке тропы Иначе тест"
                        + " wheatFieldAndOrchard_HaveWalkway_NeedShovel перестанет ловить живой случай 25.11."
                        + " При смене wheatFieldAndOrchard_HaveWalkway_NeedShovel проверь StagePlacePost"
                        + " и SchematicActions.isWalkway.");
        assertTrue(
                SchematicGround.walkway(schematic("orchard_1")),
                "У сада тоже есть тропа: лопата нужна ему наравне с грядкой Иначе тест"
                        + " wheatFieldAndOrchard_HaveWalkway_NeedShovel перестанет ловить эту поломку."
                        + " При смене wheatFieldAndOrchard_HaveWalkway_NeedShovel проверь orchard_1.");
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

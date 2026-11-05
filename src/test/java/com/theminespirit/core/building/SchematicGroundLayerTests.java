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
    void orchard_BottomLayerHasTorches_StandsOnPlotLevel() {
        assertFalse(
                SchematicGround.groundLayer(schematic("orchard_1")),
                "У сада на нижнем слое стоят факелы и саженцы: это не слой земли, сдвиг сломал бы посадку");
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

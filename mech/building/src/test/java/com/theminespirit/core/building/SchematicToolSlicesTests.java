package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.reload.DataTables;
import org.junit.jupiter.api.Test;

class SchematicToolSlicesTests {

    private static Schematic schematic(String id) {
        return DataTables.INSTANCE
                .presets()
                .current()
                .byId(id)
                .orElseThrow(() -> new AssertionError("Нет пресета " + id))
                .schematic();
    }

    @Test
    void slices_GroundedScheme_ShowsLayerBelowOrigin() {
        String garden = SchematicTool.slices(schematic("orchard_1"), BuildRotation.NONE);
        String street = SchematicTool.slices(schematic("street7_straight"), BuildRotation.NONE);

        assertTrue(
                garden.contains("y-1:"),
                "Слой земли схемы лежит на уровень ниже начала, и срез обязан показать y-1: иначе"
                        + " scripts/schematic.sh show падает на любой земляной постройке, и схему покрытия"
                        + " улицы нельзя проверить глазами. При смене сдвига перепроверь SchematicPlacement.entries"
                        + " и SchematicGround.groundLayer.");
        assertTrue(
                street.contains("y0:") && street.contains("y1:") && street.contains("y2:") && !street.contains("y3:"),
                "Срез показывает ровно sizeY слоёв: покрытие сдвинуто на y−1, а воздух над ним — y0…y2. Иначе"
                        + " не видно, сколько свободных уровней заявлено в плитке улицы, и высота коридора аудита"
                        + " проверяется только живой проверкой. При смене слоёв перепроверь street7_straight.txt и"
                        + " size в схемах улиц.");
    }
}

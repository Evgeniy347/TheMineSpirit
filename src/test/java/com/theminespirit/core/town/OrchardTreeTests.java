package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrchardTreeTests {
    private static final Path ROOT = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void orchardTrees_SchemeSoftSaplings_SameCells() throws IOException {
        Schematic scheme = SchematicTextParser.parse(Files.readString(ROOT.resolve("orchard_1.txt")), "orchard_1")
                .schematic();
        int baseY = SITE.anchor().y();
        List<BlockCoord> expected = scheme.blocks().stream()
                .filter(entry -> entry.optional() && entry.block().endsWith("_sapling"))
                .map(entry -> new BlockCoord(
                        SITE.orchard().x() + entry.dx(),
                        baseY + entry.dy(),
                        SITE.orchard().z() + entry.dz()))
                .toList();

        assertEquals(
                expected,
                SITE.orchardSaplings(),
                "Места деревьев сада берутся из мягких клеток-саженцев схемы orchard_1: жёсткий список в коде "
                        + "разошёлся бы со схемой и защита рубила бы не те колонки");
        assertEquals(4, expected.size(), "В саду уровня 1 четыре дерева: " + expected);
    }

    @Test
    void orchardTree_SaplingColumn_PlannedFromSaplingToCrown() {
        BlockCoord spot = SITE.orchardSaplings().get(0);
        int y = SITE.anchor().y();

        assertEquals(
                List.of(true, true, false, false),
                List.of(
                        SITE.orchardTree(spot.x(), y, spot.z()),
                        SITE.orchardTree(spot.x(), y + 5, spot.z()),
                        SITE.orchardTree(spot.x() + 1, y, spot.z()),
                        SITE.orchardTree(spot.x(), y + StageSite.ORCHARD_TREE_HEIGHT + 1, spot.z())),
                "Саженец и выросшее дерево сада — часть плана: снос, расчистка и фоновая рубка их не трогают; "
                        + "соседняя колонка и высота выше кроны — уже не сад");
    }

    @Test
    void planCell_OrchardSapling_InsidePlan() {
        BlockCoord spot = SITE.orchardSaplings().get(0);

        assertTrue(
                SITE.planCell(spot.x(), spot.y(), spot.z()),
                "Клетка дерева сада входит в план этапа: иначе аудит и защита не отличили бы её от дикого леса");
    }
}

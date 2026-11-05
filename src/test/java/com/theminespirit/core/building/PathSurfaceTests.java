package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PathSurfaceTests {
    private static final BlockCoord FEET = new BlockCoord(10, 70, -20);

    @Test
    void orchard_PathOnFlatGround_LiesAtSurfaceHeight() {
        Schematic orchard = schematic("orchard_1");
        List<Schematic.PlacedEntry> entries = orchard.entries(FEET, BuildRotation.NONE);
        List<Schematic.PlacedEntry> paths = paths(entries);

        assertFalse(paths.isEmpty(), "В схеме сада есть тропа: иначе тест не проверяет её уровень");
        for (Schematic.PlacedEntry path : paths) {
            assertEquals(
                    FEET.y() - 1,
                    path.pos().y(),
                    "Тропа сада заменяет верхний блок земли на уровне поверхности (якорь " + FEET
                            + " — клетка под ногами), а не встаёт блоком над ней: " + path.pos());
        }
    }

    @Test
    void orchard_ObjectsOnPath_StayAtFeetHeight() {
        Schematic orchard = schematic("orchard_1");
        List<Schematic.PlacedEntry> entries = orchard.entries(FEET, BuildRotation.NONE);

        for (Schematic.PlacedEntry entry : entries) {
            String path = SchematicBlocks.path(entry.block());
            if (!"dirt_path".equals(path)) {
                assertEquals(
                        FEET.y(),
                        entry.pos().y(),
                        "Факел и саженец стоят на поверхности, а не тонут вместе с тропой: " + entry.pos());
            }
        }
    }

    @Test
    void orchard_LegacyPathAboveSurface_IsClearedAirCell() {
        Schematic orchard = schematic("orchard_1");
        List<BlockCoord> air = orchard.airCells(FEET, BuildRotation.NONE);
        Schematic.PlacedEntry path =
                paths(orchard.entries(FEET, BuildRotation.NONE)).get(0);

        assertTrue(
                air.contains(new BlockCoord(path.pos().x(), FEET.y(), path.pos().z())),
                "Клетка над тропой — воздух: старую тропу, поставленную блоком над поверхностью, стройка снимает. "
                        + "Воздух: " + air);
    }

    @Test
    void streetPatch_OnFlatGround_PathAtSurfaceHeight() {
        Schematic patch = schematic("street_patch");

        assertEquals(
                FEET.y() - 1,
                patch.entries(FEET, BuildRotation.NONE).get(0).pos().y(),
                "Заплатка улицы ложится в слой земли под ногами: тропа не висит над улицей");
    }

    @Test
    void wheatField_PathFarmlandAndWater_AllAtSurfaceHeight() {
        Schematic field = schematic("wheat_field_1");
        List<Schematic.PlacedEntry> entries = field.entries(FEET, BuildRotation.NONE);

        for (Schematic.PlacedEntry entry : entries) {
            String path = SchematicBlocks.path(entry.block());
            boolean inGround = "dirt_path".equals(path) || "farmland".equals(path) || "water".equals(path);
            if (inGround) {
                assertEquals(
                        FEET.y() - 1,
                        entry.pos().y(),
                        "Нижний слой грядки (" + path + ") заменяет землю, а не стоит на ней: " + entry.pos());
            } else {
                assertTrue(
                        entry.pos().y() >= FEET.y(), "Посев стоит на поверхности, а не тонет в землю: " + entry.pos());
            }
        }
    }

    @Test
    void wheatField_SproutsOnSunkGround_StayOneAboveSurface() {
        Schematic field = schematic("wheat_field_1");
        List<Schematic.PlacedEntry> sprouts = field.entries(FEET, BuildRotation.NONE).stream()
                .filter(entry -> "wheat".equals(SchematicBlocks.path(entry.block())))
                .toList();

        assertFalse(sprouts.isEmpty(), "В схеме грядки есть посев: иначе тест не проверяет его высоту");
        for (Schematic.PlacedEntry sprout : sprouts) {
            assertEquals(
                    FEET.y(),
                    sprout.pos().y(),
                    "Посев стоит на поверхности (якорь " + FEET + " — клетка под ногами), а не двумя блоками "
                            + "над землёй: " + sprout.pos());
        }
    }

    @Test
    void cocoa_LogColumn_KeepsTwoTallStackWithoutCollision() {
        Schematic cocoa = schematic("cocoa_1");
        List<Schematic.PlacedEntry> logs = cocoa.entries(FEET, BuildRotation.NONE).stream()
                .filter(entry -> "jungle_log".equals(SchematicBlocks.path(entry.block())))
                .toList();

        assertFalse(logs.isEmpty(), "В схеме какао есть столбы: иначе тест не проверяет высоту стопки");
        for (int x = 2; x <= 6; x += 4) {
            for (int z = 2; z <= 6; z += 4) {
                int columnX = FEET.x() + x;
                int columnZ = FEET.z() + z;
                List<Integer> heights = logs.stream()
                        .filter(entry ->
                                entry.pos().x() == columnX && entry.pos().z() == columnZ)
                        .map(entry -> entry.pos().y())
                        .sorted()
                        .toList();
                assertEquals(
                        List.of(FEET.y(), FEET.y() + 1),
                        heights,
                        "Столб какао — два блока над землёй: столбец предметов не сдвигается вниз, иначе два "
                                + "бревна сойдутся в одной клетке. Высоты: " + heights);
            }
        }
        long unique = cocoa.entries(FEET, BuildRotation.NONE).stream()
                .map(Schematic.PlacedEntry::pos)
                .distinct()
                .count();
        assertEquals(
                cocoa.entries(FEET, BuildRotation.NONE).size(),
                unique,
                "Две клетки схемы не имеют права встать в одну: иначе часть постройки молча теряется");
    }

    @Test
    void walkwayPlaced_PathOneBelowCell_CountsWhenCellIsPassable() {
        assertTrue(
                SchematicGround.walkwayPlaced("minecraft:dirt_path", "minecraft:air", "minecraft:dirt_path", true),
                "На неровной земле лопата кладёт тропу в поверхность ниже клетки схемы: аудит обязан её считать");
    }

    @Test
    void walkwayPlaced_PathAboveStandingPath_NotCountedAsSurface() {
        assertFalse(
                SchematicGround.walkwayPlaced(
                        "minecraft:dirt_path", "minecraft:grass_block", "minecraft:dirt_path", false),
                "Тропа в воздухе над дёрном — висящая: аудит обязан требовать её в поверхности, а не над ней");
    }

    private static List<Schematic.PlacedEntry> paths(List<Schematic.PlacedEntry> entries) {
        List<Schematic.PlacedEntry> paths = new ArrayList<>();
        for (Schematic.PlacedEntry entry : entries) {
            if (SchematicActions.isWalkway(entry.block())) {
                paths.add(entry);
            }
        }
        return List.copyOf(paths);
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

package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class MineLayoutTests {
    private static final String DESCENT = """
            type: underground
            size: 1x4x1
            entrance: 0,3,0
            repeat:
              step: [1, -1, 0]
              until: "y=50"
            legend:
              '#': @floor
            layers:
              - y0: |
                  #
              - y1: |
                  .
              - y2: |
                  .
              - y3: |
                  .
            """;

    private static MineLayout descent(BlockCoord origin, BuildRotation rotation, MineLayout.Extent extent) {
        SchematicDocument document = SchematicTextParser.parse(DESCENT, "спуск.txt");
        return MineLayout.place(
                document.schematic(), document.header().entrance().orElseThrow(), origin, rotation, extent);
    }

    @Test
    void place_DescentUntilY50FromY64_FloorsDownTo50() {
        MineLayout layout = descent(new BlockCoord(10, 64, 20), BuildRotation.NONE, MineLayout.Extent.NONE);

        assertEquals(
                15,
                layout.floors().size(),
                "спуск от пола y=64 до y=50 — 15 ступеней по шагу [1,-1,0]; фактически "
                        + layout.floors().size());
        assertTrue(
                layout.floors().contains(new BlockCoord(24, 50, 20)),
                "нижняя ступень — пол на y=50 через 14 шагов по x: цель спуска из файла владельца");
        assertEquals(
                new BlockCoord(10, 67, 20),
                layout.entrance(),
                "вход — верхняя клетка первой ступени (0,3,0) от якоря: проходка начинается от него");
    }

    @Test
    void place_UntilYOverride_StopsAtGivenY() {
        MineLayout layout = descent(new BlockCoord(0, -56, 0), BuildRotation.NONE, new MineLayout.Extent(-62, null));

        assertEquals(
                7,
                layout.floors().size(),
                "стройка задаёт свою глубину (until_y) вместо y=50 из файла: от -56 до -62 — 7 ступеней");
    }

    @Test
    void place_Clockwise90_FirstSegmentStaysAtAnchorAndDescendsAlongZ() {
        MineLayout layout =
                descent(new BlockCoord(0, 10, 0), BuildRotation.CLOCKWISE_90, new MineLayout.Extent(8, null));

        assertTrue(
                layout.floors().contains(new BlockCoord(0, 10, 0))
                        && layout.floors().contains(new BlockCoord(0, 9, 1))
                        && layout.floors().contains(new BlockCoord(0, 8, 2)),
                "поворот на 90° по часовой разворачивает спуск вдоль +z, первая ступень — у якоря; фактически "
                        + layout.floors());
    }
}

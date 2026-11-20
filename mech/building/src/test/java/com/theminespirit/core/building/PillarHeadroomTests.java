package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PillarHeadroomTests {
    private static final BlockCoord BASE = new BlockCoord(10, 64, -5);

    @Test
    void firstBlocked_CeilingWithinHeight_NamesCellAndBlock() {
        PillarHeadroom.View view = view(Map.of(BASE.y() + 3, "minecraft:stone"), Set.of());

        PillarHeadroom.Blockage found =
                PillarHeadroom.firstBlocked(view, BASE, 5).orElseThrow();

        assertEquals(
                new BlockCoord(BASE.x(), BASE.y() + 3, BASE.z()),
                found.cell(),
                "Препятствие обязано называться точной клеткой: иначе дух снова уходит в stall на 200 тиков "
                        + "без понятной причины; при смене PillarHeadroom проверь индекс от основания");
        assertEquals(
                "minecraft:stone",
                found.block(),
                "Препятствие обязано называть блок: без этого в живом логе только «потолок», и непонятно, "
                        + "что сносить; при смене View.block проверь id из мира");
    }

    @Test
    void firstBlocked_ClearColumn_ReportsNothing() {
        PillarHeadroom.View view = view(Map.of(), Set.of());

        assertEquals(
                Optional.empty(),
                PillarHeadroom.firstBlocked(view, BASE, 5),
                "Свободный столб не имеет препятствий: иначе законные подмости перестанут строиться, и "
                        + "расчистка высоких клеток встанет; при смене проверки replaceable проверь этот случай");
    }

    @Test
    void firstBlocked_WaterInColumn_IsBlocked() {
        PillarHeadroom.View view = view(Map.of(BASE.y() + 1, "minecraft:water"), Set.of(BASE.y() + 1));

        assertTrue(
                PillarHeadroom.firstBlocked(view, BASE, 4).isPresent(),
                "Вода в столбе — препятствие: без этого дух строит столб в воду и тонет; при смене "
                        + "View.fluid проверь воду как блокировку");
    }

    private static PillarHeadroom.View view(Map<Integer, String> blocks, Set<Integer> fluids) {
        return new PillarHeadroom.View() {
            @Override
            public String block(BlockCoord at) {
                return blocks.getOrDefault(at.y(), "minecraft:air");
            }

            @Override
            public boolean replaceable(BlockCoord at) {
                return "minecraft:air".equals(block(at));
            }

            @Override
            public boolean fluid(BlockCoord at) {
                return fluids.contains(at.y());
            }
        };
    }
}

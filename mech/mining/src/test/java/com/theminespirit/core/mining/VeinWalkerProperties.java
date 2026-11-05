package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class VeinWalkerProperties {
    private static final int GRID_SIZE = 4;
    private static final int NO_LIMIT = 1000;

    @Property
    void collect_RandomWorld_MatchesConnectedComponent(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        CountingOreView view = randomView(new Random(seed));
        if (view.ores().isEmpty()) {
            return;
        }
        BlockCoord start = view.ores().stream().min(Comparator.naturalOrder()).orElseThrow();

        Vein vein = VeinWalker.collect(start, view, NO_LIMIT);

        assertEquals(
                view.component(start),
                new HashSet<>(vein.blocks()),
                "Обход обязан вернуть ровно связную компоненту старта: пропуск или лишний блок искажает жилу "
                        + "и сбивает счёт добытой руды");
        assertFalse(
                vein.truncated(),
                "Пока блоков меньше предела, жила не помечается усечённой: иначе адаптер преждевременно ищет новую");
    }

    @Property
    void collect_RandomWorld_ReadsStayBounded(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        CountingOreView view = randomView(new Random(seed));
        if (view.ores().isEmpty()) {
            return;
        }
        BlockCoord start = view.ores().stream().min(Comparator.naturalOrder()).orElseThrow();

        Vein vein = VeinWalker.collect(start, view, NO_LIMIT);
        int size = vein.blocks().size();

        assertTrue(
                view.reads() <= 27 * size + 27,
                "Число чтений OreView обязано быть линейным по размеру жилы (27 × размер + 27): иначе обход "
                        + "большой жилы многократно перечитывает клетки и кладёт серверный тик. Чтений: "
                        + view.reads() + ", блоков: " + size);
    }

    private static CountingOreView randomView(Random random) {
        CountingOreView view = new CountingOreView();
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int z = 0; z < GRID_SIZE; z++) {
                    if (random.nextDouble() < 0.3) {
                        view.withOre(x, y, z);
                    }
                }
            }
        }
        return view;
    }
}

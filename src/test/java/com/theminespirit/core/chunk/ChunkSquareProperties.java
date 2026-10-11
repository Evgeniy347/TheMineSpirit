package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Tag;

class ChunkSquareProperties {
    @Property
    void chunks_AnyRadius_ContainsExactSquareSize(@ForAll @IntRange(min = 0, max = 32) int radius) {
        Set<ChunkCoord> chunks = ChunkSquare.chunks(new ChunkCoord(0, 0), radius);

        assertEquals((2 * radius + 1) * (2 * radius + 1), chunks.size(),
                "Для любого радиуса от 0 до 32 размер квадрата обязан быть (2R+1)^2: иначе дух держит "
                        + "не ту зону, а на границе чанк либо теряется, либо грузится лишний");
    }

    @Tag("slow")
    @Property
    void diff_AnyCenters_RemovesAndAddsToReachNewSquare(
            @ForAll @IntRange(min = -1000, max = 1000) int oldX,
            @ForAll @IntRange(min = -1000, max = 1000) int oldZ,
            @ForAll @IntRange(min = -1000, max = 1000) int newX,
            @ForAll @IntRange(min = -1000, max = 1000) int newZ,
            @ForAll @IntRange(min = 0, max = 16) int radius) {
        ChunkCoord oldCenter = new ChunkCoord(oldX, oldZ);
        ChunkCoord newCenter = new ChunkCoord(newX, newZ);
        ChunkDiff diff = ChunkSquare.diff(oldCenter, newCenter, radius);

        Set<ChunkCoord> applied = ChunkSquare.apply(ChunkSquare.chunks(oldCenter, radius), diff);

        assertEquals(ChunkSquare.chunks(newCenter, radius), applied,
                "Применённый diff обязан переводить старый квадрат в новый при любых центрах: билеты после "
                        + "сдвига должны совпадать с зоной вокруг духа, ни больше ни меньше");
    }

    @Property(tries = 200)
    void diff_WalkOfSteps_CountsOneRowAddedAndRemovedPerStep(
            @ForAll @IntRange(min = 1, max = 64) int steps,
            @ForAll @IntRange(min = 0, max = 10) int radius) {
        Set<ChunkCoord> current = ChunkSquare.chunks(new ChunkCoord(0, 0), radius);
        int added = 0;
        int removed = 0;
        for (int step = 1; step <= steps; step++) {
            ChunkDiff diff = ChunkSquare.diff(new ChunkCoord(step - 1, 0), new ChunkCoord(step, 0), radius);
            added += diff.toAdd().size();
            removed += diff.toRemove().size();
            current = ChunkSquare.apply(current, diff);
        }
        int row = 2 * radius + 1;

        assertEquals(steps * row, added,
                "N переходов на соседний чанк обязаны дать ровно N*(2R+1) добавлений: иначе набор не "
                        + "сдвигается, и дух тянет за собой старые чанки");
        assertEquals(steps * row, removed,
                "N переходов на соседний чанк обязаны дать ровно N*(2R+1) снятий: иначе ушедшие чанки "
                        + "остаются нагруженными и утекут");
        assertEquals(ChunkSquare.chunks(new ChunkCoord(steps, 0), radius), current,
                "После N переходов удержанный набор обязан равняться квадрату вокруг конечного центра");
    }
}

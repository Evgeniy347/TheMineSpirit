package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SchematicUpgrade {
    private SchematicUpgrade() {}

    public static List<BlockCoord> extraPositions(
            Schematic previous, Schematic next, BlockCoord origin, BuildRotation rotation) {
        Objects.requireNonNull(previous, "Старая схема не может быть null: по ней видно, что снимать");
        List<BlockCoord> cells = new ArrayList<>();
        for (Schematic.PlacedEntry entry : previous.entries(origin, rotation)) {
            cells.add(entry.pos());
        }
        return extraPositions(cells, next, origin, rotation);
    }

    public static List<BlockCoord> extraPositions(
            Collection<BlockCoord> previous, Schematic next, BlockCoord nextOrigin, BuildRotation nextRotation) {
        Objects.requireNonNull(previous, "Старые клетки не могут быть null: без них нечего снимать");
        Objects.requireNonNull(next, "Новая схема не может быть null: по ней видно, что остаётся защищённым");
        Objects.requireNonNull(nextOrigin, "Якорь новой схемы не может быть null: без него не сравнить клетки");
        Objects.requireNonNull(nextRotation, "Поворот новой схемы не может быть null: без него не сравнить клетки");
        Set<BlockCoord> kept = new HashSet<>();
        for (Schematic.PlacedEntry entry : next.entries(nextOrigin, nextRotation)) {
            kept.add(entry.pos());
        }
        List<BlockCoord> extra = new ArrayList<>();
        for (BlockCoord cell : previous) {
            if (!kept.contains(cell)) {
                extra.add(cell);
            }
        }
        extra.sort(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()));
        return List.copyOf(extra);
    }
}

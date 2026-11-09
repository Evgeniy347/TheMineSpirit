package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class BuildStandLevel {
    private BuildStandLevel() {}

    public static ReachPlanner.World atOrAbove(ReachPlanner.World world, Collection<BlockCoord> schemeCells) {
        Objects.requireNonNull(schemeCells, "Клетки схемы не могут быть null: по ним ищется низ постройки");
        int baseY = schemeCells.stream().mapToInt(BlockCoord::y).min().orElse(Integer.MAX_VALUE) - 1;
        return atOrAbove(world, baseY);
    }

    public static ReachPlanner.World atOrAbove(ReachPlanner.World world, int baseY) {
        Objects.requireNonNull(world, "Мир стоянок не может быть null: без него не проверить опору");
        return new ReachPlanner.World() {
            @Override
            public boolean ready(BlockCoord cell) {
                return world.ready(cell);
            }

            @Override
            public boolean standable(BlockCoord feet) {
                return feet.y() >= baseY && world.standable(feet);
            }
        };
    }

    public static boolean isAction(Schematic.@Nullable PlacedEntry entry) {
        return entry != null && SchematicActions.isActionPlaced(entry.block());
    }

    public static boolean allowed(BlockCoord stand, int baseY) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: её высота сравнивается с низом постройки");
        return stand.y() >= baseY;
    }
}

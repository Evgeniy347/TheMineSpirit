package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MineApproach {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final Set<Cell> BREAKABLE = Set.of(Cell.SOLID, Cell.FALLING);

    private MineApproach() {}

    public static List<BlockCoord> opening(MineColumn entrance, BlockCoord from, SafetyView view) {
        Objects.requireNonNull(entrance, "Ступень входа не может быть null: без неё неизвестно, что открывать");
        Objects.requireNonNull(from, "Точка духа не может быть null: от неё проверяется подход");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему ищется преграда у входа");
        if (MineReach.canReach(from, entrance.stand(), view)) {
            return List.of();
        }
        for (int[] side : SIDES) {
            List<BlockCoord> cells = wall(entrance, side, from, view);
            if (cells.isEmpty()) {
                continue;
            }
            SafetyView opened = opened(view, cells);
            if (MineReach.canReach(from, entrance.stand(), opened)) {
                return cells;
            }
        }
        return List.of();
    }

    private static List<BlockCoord> wall(MineColumn entrance, int[] side, BlockCoord from, SafetyView view) {
        int x = entrance.floor().x() + side[0];
        int z = entrance.floor().z() + side[1];
        int floor = entrance.floor().y();
        int top = Math.min(from.y(), floor + entrance.height());
        List<BlockCoord> cells = new ArrayList<>(Math.max(0, top - floor));
        for (int y = top; y > floor; y--) {
            BlockCoord cell = new BlockCoord(x, y, z);
            Cell kind = view.cellAt(cell);
            if (kind == Cell.PASSABLE) {
                continue;
            }
            if (!BREAKABLE.contains(kind)) {
                return List.of();
            }
            cells.add(cell);
        }
        return cells;
    }

    private static SafetyView opened(SafetyView view, List<BlockCoord> cells) {
        Set<BlockCoord> cut = new HashSet<>(cells);
        return coord -> cut.contains(coord) ? Cell.PASSABLE : view.cellAt(coord);
    }
}

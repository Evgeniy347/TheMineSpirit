package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SealedSpace {
    private static final int MAX_CELLS = 256;

    public interface View {
        boolean passable(BlockCoord cell);

        boolean swimmable(BlockCoord cell);

        boolean sky(BlockCoord cell);
    }

    private static final List<int[]> SIDES = List.of(
            new int[] {0, 1, 0},
            new int[] {0, -1, 0},
            new int[] {1, 0, 0},
            new int[] {-1, 0, 0},
            new int[] {0, 0, 1},
            new int[] {0, 0, -1});

    private SealedSpace() {}

    public static boolean sealed(BlockCoord feet, View view) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: от них ищется выход к небу");
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не отличить проход от породы");
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        queue.add(feet);
        seen.add(feet);
        while (!queue.isEmpty()) {
            BlockCoord cell = queue.poll();
            if (view.sky(cell)) {
                return false;
            }
            if (seen.size() > MAX_CELLS) {
                return false;
            }
            for (int[] side : SIDES) {
                BlockCoord near = new BlockCoord(cell.x() + side[0], cell.y() + side[1], cell.z() + side[2]);
                if (walkable(view, near) && seen.add(near)) {
                    queue.add(near);
                }
            }
        }
        return true;
    }

    private static boolean walkable(View view, BlockCoord cell) {
        return view.passable(cell) || view.swimmable(cell);
    }
}

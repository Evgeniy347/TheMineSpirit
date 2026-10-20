package com.theminespirit.core.building;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Путь отхода (правило владельца 20.10, постоянное — в любой стройке): прежде чем поставить блок, дух проверяет, что
 * после установки от его ног остаётся пеший путь к выходу из постройки. Если блок перекроет отступление (последний
 * блок стены комнаты, проём балкона), дух сперва переходит на стоянку по ту сторону, откуда путь к выходу остаётся,
 * и ставит блок оттуда. Ходьба — как у тела 1×2: шаг вверх на 1 (голове нужно место над исходной клеткой), спуск до 3.
 */
public final class RetreatPath {
    public static final int MAX_NODES = 4096;
    static final int MAX_DROP = 3;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private RetreatPath() {}

    // Выхода нет и до установки (дух уже заперт) или обход не уложился в MAX_NODES клеток — правило не мешает:
    // блок ничего не меняет.
    public static boolean allows(BlockCoord feet, Set<BlockCoord> placed, SafetyView view, Predicate<BlockCoord> exit) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: без них неизвестно, откуда отступать");
        Objects.requireNonNull(placed, "Ставимые клетки не могут быть null: пустое множество задаётся пустым");
        Objects.requireNonNull(view, "Мир не может быть null: по нему ищется путь отхода");
        Objects.requireNonNull(exit, "Выход не может быть null: без него неизвестно, куда отступать");
        if (exit.test(feet) || placed.isEmpty()) {
            return true;
        }
        Reach after = search(feet, placed, view, exit);
        if (after != Reach.CLOSED) {
            return true;
        }
        return search(feet, Set.of(), view, exit) == Reach.CLOSED;
    }

    private enum Reach {
        OPEN,
        CLOSED,
        UNKNOWN
    }

    private static Reach search(BlockCoord feet, Set<BlockCoord> placed, SafetyView view, Predicate<BlockCoord> exit) {
        SafetyView overlay = cell -> placed.contains(cell) ? Cell.SOLID : view.cellAt(cell);
        Set<BlockCoord> seen = new HashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        seen.add(feet);
        queue.add(feet);
        while (!queue.isEmpty()) {
            if (seen.size() > MAX_NODES) {
                return Reach.UNKNOWN;
            }
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1], overlay);
                if (next == null || !seen.add(next)) {
                    continue;
                }
                if (exit.test(next)) {
                    return Reach.OPEN;
                }
                queue.add(next);
            }
        }
        return Reach.CLOSED;
    }

    private static @Nullable BlockCoord step(BlockCoord from, int dx, int dz, SafetyView view) {
        int x = from.x() + dx;
        int z = from.z() + dz;
        BlockCoord level = new BlockCoord(x, from.y(), z);
        if (MiningSafety.canStand(level, view)) {
            return level;
        }
        BlockCoord up = new BlockCoord(x, from.y() + 1, z);
        if (view.cellAt(new BlockCoord(from.x(), from.y() + 2, from.z())) == Cell.PASSABLE
                && MiningSafety.canStand(up, view)) {
            return up;
        }
        for (int drop = 1; drop <= MAX_DROP; drop++) {
            BlockCoord through = new BlockCoord(x, from.y() - drop + 1, z);
            if (view.cellAt(new BlockCoord(x, from.y() + 1, z)) != Cell.PASSABLE
                    || view.cellAt(through) != Cell.PASSABLE) {
                return null;
            }
            BlockCoord down = new BlockCoord(x, from.y() - drop, z);
            if (MiningSafety.canStand(down, view)) {
                return down;
            }
        }
        return null;
    }
}

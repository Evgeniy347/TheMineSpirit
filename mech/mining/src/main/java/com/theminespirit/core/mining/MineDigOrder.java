package com.theminespirit.core.mining;

import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class MineDigOrder {
    private MineDigOrder() {}

    public static List<BlockCoord> from(BlockCoord stand, List<BlockCoord> cells, SafetyView view) {
        Objects.requireNonNull(cells, "Клетки не могут быть null: пустой список — ломать нечего");
        return from(stand, cells, view, List.of());
    }

    private static List<BlockCoord> from(
            BlockCoord stand, List<BlockCoord> cells, SafetyView view, Collection<BlockCoord> first) {
        Objects.requireNonNull(stand, "Стоянка не может быть null: с неё видно, какую клетку ломать следующей");
        Objects.requireNonNull(cells, "Клетки не могут быть null: пустой список — ломать нечего");
        Objects.requireNonNull(view, "Вид мира не может быть null: без него не проверить видимость клетки");
        Objects.requireNonNull(first, "Клетки первого сорта не могут быть null: пусто — сначала все подряд");
        List<BlockCoord> remaining = new ArrayList<>(cells);
        remaining.sort(preferred(first));
        List<BlockCoord> order = new ArrayList<>(remaining.size());
        List<BlockCoord> opened = new ArrayList<>(remaining.size());
        while (!remaining.isEmpty()) {
            BlockCoord next = firstVisible(stand, remaining, opened, view);
            if (next == null) {
                next = remaining.get(0);
            }
            remaining.remove(next);
            opened.add(next);
            order.add(next);
        }
        return List.copyOf(order);
    }

    private static Comparator<BlockCoord> preferred(Collection<BlockCoord> first) {
        return Comparator.<BlockCoord>comparingInt(cell -> first.contains(cell) ? 0 : 1)
                .thenComparing(Comparator.comparingInt(BlockCoord::y).reversed())
                .thenComparing(Comparator.naturalOrder());
    }

    public static List<BlockCoord> column(
            MineColumn column, Collection<BlockCoord> walls, BlockCoord stand, SafetyView view) {
        List<BlockCoord> passage = new ArrayList<>(column.cells());
        List<BlockCoord> targets = new ArrayList<>(passage);
        targets.addAll(walls);
        targets.add(column.floor());
        return from(stand, targets, view, passage);
    }

    public static boolean visible(BlockCoord stand, BlockCoord cell, List<BlockCoord> opened, SafetyView view) {
        return BlockSight.visible(
                        BlockSight.eyes(stand), cell, coord -> MineTerrain.of(view.cellAt(coord)), opened::contains)
                == Visibility.CLEAR;
    }

    private static @Nullable BlockCoord firstVisible(
            BlockCoord stand, List<BlockCoord> remaining, List<BlockCoord> opened, SafetyView view) {
        for (BlockCoord cell : remaining) {
            if (visible(stand, cell, opened, view)) {
                return cell;
            }
        }
        return null;
    }
}

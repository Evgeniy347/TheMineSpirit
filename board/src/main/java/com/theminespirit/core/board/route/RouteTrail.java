package com.theminespirit.core.board.route;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RouteTrail(List<BlockCoord> cells, String block) {
    public RouteTrail {
        cells = List.copyOf(
                Objects.requireNonNull(cells, "Клетки тропы не могут быть null: нет клеток — пустая тропа"));
        Objects.requireNonNull(block, "Материал тропы не может быть null: по нему убирают проложенные блоки");
    }

    public static RouteTrail of(PlotLeveling path) {
        Objects.requireNonNull(path, "План тропы не может быть null: из него берут проложенные клетки");
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (PlotLeveling.Box box : path.fill()) {
            for (int x = box.x1(); x <= box.x2(); x++) {
                for (int y = box.y1(); y <= box.y2(); y++) {
                    for (int z = box.z1(); z <= box.z2(); z++) {
                        cells.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        return new RouteTrail(List.copyOf(cells), path.block());
    }

    public boolean empty() {
        return cells.isEmpty();
    }

    public BlockCoord first() {
        return cells.stream()
                .min(Comparator.comparingInt(BlockCoord::y)
                        .thenComparingInt(BlockCoord::x)
                        .thenComparingInt(BlockCoord::z))
                .orElseThrow(() -> new IllegalStateException("У пустой тропы нет первой клетки: снимать нечего"));
    }

    public PlotLeveling removal() {
        List<PlotLeveling.Box> cut = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            cut.add(new PlotLeveling.Box(cell.x(), cell.y(), cell.z(), cell.x(), cell.y(), cell.z()));
        }
        return new PlotLeveling(cut, List.of(), block);
    }
}

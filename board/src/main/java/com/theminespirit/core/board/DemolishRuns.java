package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class DemolishRuns {
    record Run(int x, int z, int bottom, int top) {
        BlockCoord low() {
            return new BlockCoord(x, bottom, z);
        }
    }

    private DemolishRuns() {}

    static List<Run> runs(List<BlockCoord> cells) {
        Map<String, List<BlockCoord>> columns = new TreeMap<>();
        for (BlockCoord cell : cells) {
            columns.computeIfAbsent(cell.x() + "," + cell.z(), ignored -> new ArrayList<>())
                    .add(cell);
        }
        List<Run> runs = new ArrayList<>();
        columns.values().forEach(column -> {
            int[] xz = {column.get(0).x(), column.get(0).z()};
            List<Integer> sorted =
                    column.stream().map(BlockCoord::y).distinct().sorted().toList();
            int start = sorted.get(0);
            int previous = start;
            for (int i = 1; i < sorted.size(); i++) {
                int y = sorted.get(i);
                if (y != previous + 1) {
                    runs.add(new Run(xz[0], xz[1], start, previous));
                    start = y;
                }
                previous = y;
            }
            runs.add(new Run(xz[0], xz[1], start, previous));
        });
        runs.sort(Comparator.comparingInt(Run::bottom).thenComparing(Run::low));
        return List.copyOf(runs);
    }
}

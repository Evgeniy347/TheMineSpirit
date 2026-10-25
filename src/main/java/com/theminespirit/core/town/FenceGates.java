package com.theminespirit.core.town;

import com.theminespirit.core.building.FenceGateRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public final class FenceGates {
    private static final int SHIFT = 3;

    private FenceGates() {}

    public static FenceLine around(ColumnMask area, TownStreets streets) {
        Objects.requireNonNull(streets, "Улицы не могут быть null: где улица пересекает ограду — ворота");
        List<ColumnMask.Cell> loop = FenceLine.loop(area);
        FenceLine bare = new FenceLine(loop, List.of());
        List<Integer> required = crossings(loop, streets);
        List<Integer> splits = required.isEmpty() ? corners(loop) : required;
        SortedSet<Integer> gates = new TreeSet<>(required);
        int size = loop.size();
        for (int index = 0; index < splits.size(); index++) {
            int from = splits.get(index);
            int to = index + 1 < splits.size() ? splits.get(index + 1) : splits.get(0) + size;
            int previous = from;
            List<Integer> offsets = FenceGateRule.gates(to - from + 1);
            for (int k = 0; k < offsets.size(); k++) {
                int next = k + 1 < offsets.size() ? from + offsets.get(k + 1) : to;
                previous = straightNear(bare, from + offsets.get(k), previous, next);
                gates.add(Math.floorMod(previous, size));
            }
        }
        return new FenceLine(loop, List.copyOf(gates));
    }

    public static List<Integer> spans(FenceLine line) {
        Objects.requireNonNull(line, "Ограда не может быть null");
        List<Integer> spans = new ArrayList<>();
        List<Integer> gates = line.gates();
        for (int index = 0; index < gates.size(); index++) {
            int next = index + 1 < gates.size()
                    ? gates.get(index + 1)
                    : gates.get(0) + line.posts().size();
            spans.add(next - gates.get(index));
        }
        return List.copyOf(spans);
    }

    private static List<Integer> crossings(List<ColumnMask.Cell> loop, TownStreets streets) {
        List<Integer> gates = new ArrayList<>();
        int size = loop.size();
        int start = 0;
        while (start < size && onStreet(loop.get(start), streets)) {
            start++;
        }
        if (start == size) {
            return List.of();
        }
        int run = -1;
        for (int step = 1; step <= size; step++) {
            int index = (start + step) % size;
            boolean street = onStreet(loop.get(index), streets) && step < size;
            if (street && run < 0) {
                run = step;
            } else if (!street && run >= 0) {
                gates.add((start + (run + step - 1) / 2) % size);
                run = -1;
            }
        }
        return gates.stream().sorted().toList();
    }

    private static boolean onStreet(ColumnMask.Cell cell, TownStreets streets) {
        return streets.contains(cell.x(), cell.z());
    }

    private static int straightNear(FenceLine line, int target, int previous, int next) {
        int size = line.posts().size();
        for (int shift = 0; shift <= SHIFT; shift++) {
            for (int sign : new int[] {-1, 1}) {
                int at = target + sign * shift;
                if (at > previous
                        && at - previous <= FenceGateRule.MAX_SPAN
                        && next - at <= FenceGateRule.MAX_SPAN
                        && line.straight(Math.floorMod(at, size))) {
                    return at;
                }
            }
        }
        return target;
    }

    private static List<Integer> corners(List<ColumnMask.Cell> loop) {
        int[] best = {0, 0, 0, 0};
        for (int index = 1; index < loop.size(); index++) {
            ColumnMask.Cell cell = loop.get(index);
            int[] keys = {-(cell.x() + cell.z()), cell.x() - cell.z(), cell.x() + cell.z(), cell.z() - cell.x()};
            for (int corner = 0; corner < keys.length; corner++) {
                ColumnMask.Cell kept = loop.get(best[corner]);
                int[] old = {-(kept.x() + kept.z()), kept.x() - kept.z(), kept.x() + kept.z(), kept.z() - kept.x()};
                if (keys[corner] > old[corner]) {
                    best[corner] = index;
                }
            }
        }
        return Arrays.stream(best).distinct().sorted().boxed().toList();
    }
}

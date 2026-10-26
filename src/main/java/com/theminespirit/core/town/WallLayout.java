package com.theminespirit.core.town;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.PresetLibrary;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class WallLayout {
    private final Map<String, List<Variant>> variants = new HashMap<>();
    private final int longest;

    public WallLayout(List<WallFragment> fragments) {
        Objects.requireNonNull(fragments, "Фрагменты стены не могут быть null: линия раскладывается на них");
        int longest = 0;
        for (WallFragment fragment : fragments) {
            longest = Math.max(longest, fragment.cells().size());
            for (BuildRotation rotation : BuildRotation.values()) {
                WallFragment.Shape shape = fragment.shape(rotation);
                variants.computeIfAbsent(shape.key(), key -> new ArrayList<>())
                        .add(new Variant(fragment.scheme(), rotation, shape));
            }
        }
        this.longest = longest;
    }

    public static WallLayout of(PresetLibrary presets, List<String> schemes) {
        Objects.requireNonNull(presets, "Библиотека схем не может быть null: фрагменты стены — схемы");
        return new WallLayout(schemes.stream()
                .map(scheme -> WallFragment.of(
                        scheme,
                        presets.byId(scheme)
                                .orElseThrow(() -> new IllegalStateException("presets.json: нет схемы фрагмента '"
                                        + scheme + "' — линию стены не разложить"))
                                .schematic()))
                .toList());
    }

    public List<WallPiece> closed(List<ColumnMask.Cell> line, Collection<Integer> gates) {
        int size = line.size();
        Set<Integer> starts = new LinkedHashSet<>();
        int base = gates.stream().mapToInt(Integer::intValue).min().orElse(0);
        for (int back = 0; back < longest; back++) {
            starts.add(Math.floorMod(base - back, size));
        }
        List<WallPiece> best = null;
        for (int start : starts) {
            List<WallPiece> laid = lay(line, Set.copyOf(gates), start, size);
            if (laid != null && (best == null || laid.size() < best.size())) {
                best = laid;
            }
        }
        return checked(best, line);
    }

    public List<WallPiece> open(List<ColumnMask.Cell> line, Collection<Integer> gates) {
        return checked(lay(line, Set.copyOf(gates), 0, line.size()), line);
    }

    private static List<WallPiece> checked(@Nullable List<WallPiece> laid, List<ColumnMask.Cell> line) {
        if (laid == null) {
            throw new IllegalStateException("Линию стены из " + line.size() + " клеток не разложить на фрагменты: "
                    + "калитка не на прямом участке или нет фрагмента-столба — проверьте калитки ограды и набор схем");
        }
        return laid;
    }

    private @Nullable List<WallPiece> lay(List<ColumnMask.Cell> line, Set<Integer> gates, int start, int size) {
        int[] cost = new int[size + 1];
        WallPiece[] step = new WallPiece[size + 1];
        Arrays.fill(cost, Integer.MAX_VALUE);
        cost[0] = 0;
        for (int at = 0; at < size; at++) {
            if (cost[at] == Integer.MAX_VALUE) {
                continue;
            }
            for (int length = 1; length <= longest && at + length <= size; length++) {
                WallPiece piece = piece(line, gates, Math.floorMod(start + at, line.size()), length);
                if (piece != null && cost[at] + 1 < cost[at + length]) {
                    cost[at + length] = cost[at] + 1;
                    step[at + length] = piece;
                }
            }
        }
        if (cost[size] == Integer.MAX_VALUE) {
            return null;
        }
        List<WallPiece> pieces = new ArrayList<>();
        for (int end = size; end > 0; end -= step[end].length()) {
            pieces.add(0, step[end]);
        }
        return List.copyOf(pieces);
    }

    private @Nullable WallPiece piece(List<ColumnMask.Cell> line, Set<Integer> gates, int from, int length) {
        List<ColumnMask.Cell> cells = new ArrayList<>(length);
        List<ColumnMask.Cell> gateCells = new ArrayList<>();
        for (int offset = 0; offset < length; offset++) {
            int index = (from + offset) % line.size();
            cells.add(line.get(index));
            if (gates.contains(index)) {
                gateCells.add(line.get(index));
            }
        }
        if (gateCells.size() > 1) {
            return null;
        }
        int minX = cells.stream().mapToInt(ColumnMask.Cell::x).min().orElseThrow();
        int minZ = cells.stream().mapToInt(ColumnMask.Cell::z).min().orElseThrow();
        for (Variant variant : variants.getOrDefault(WallFragment.key(cells), List.of())) {
            int x = minX - variant.shape().minX();
            int z = minZ - variant.shape().minZ();
            ColumnMask.Cell gate = variant.shape().gate();
            boolean fits = gate == null
                    ? gateCells.isEmpty()
                    : gateCells.size() == 1 && gateCells.get(0).equals(new ColumnMask.Cell(x + gate.x(), z + gate.z()));
            if (fits) {
                return new WallPiece(variant.scheme(), variant.rotation(), x, z, from, length);
            }
        }
        return null;
    }

    private record Variant(String scheme, BuildRotation rotation, WallFragment.Shape shape) {}
}

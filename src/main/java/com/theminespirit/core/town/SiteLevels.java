package com.theminespirit.core.town;

import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntBinaryOperator;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

public final class SiteLevels {
    private SiteLevels() {}

    public static int level(StageSite site) {
        Objects.requireNonNull(site, "Место этапа не может быть null: уровень поселения берётся из него");
        return site.anchor().y() - 1;
    }

    public static int fenceY(StageSite site) {
        return level(site) + 1;
    }

    public static PlotLeveling leveling(StageSite site, IntBinaryOperator tops) {
        BlockCoord chest = chestSpot(site);
        return new PlotLeveling(
                extra(site, tops, cell -> cell.x() == chest.x() && cell.z() == chest.z(), false),
                List.of(),
                PlotLeveling.DEFAULT_BLOCK);
    }

    public static List<PlotLeveling.Box> extra(StageSite site, IntBinaryOperator tops) {
        return extra(site, tops, cell -> false);
    }

    public static List<PlotLeveling.Box> extra(
            StageSite site, IntBinaryOperator tops, Predicate<ColumnMask.Cell> skip) {
        return extra(site, tops, skip, true);
    }

    private static List<PlotLeveling.Box> extra(
            StageSite site, IntBinaryOperator tops, Predicate<ColumnMask.Cell> skip, boolean protectBuildings) {
        Objects.requireNonNull(tops, "Высоты колонок не могут быть null: без них лишнего над уровнем не найти");
        Objects.requireNonNull(skip, "Пропускаемые колонки не могут быть null: нет таких — всегда false");
        int level = level(site);
        Map<Integer, Map<Integer, List<Integer>>> layers = new TreeMap<>();
        for (ColumnMask.Cell column : site.level().cells()) {
            if (skip.test(column)) {
                continue;
            }
            int top = tops.applyAsInt(column.x(), column.z());
            for (int y = level + 1; y < top; y++) {
                if (!protectBuildings || !building(site, column.x(), y, column.z())) {
                    layers.computeIfAbsent(y, key -> new TreeMap<>())
                            .computeIfAbsent(column.z(), key -> new ArrayList<>())
                            .add(column.x());
                }
            }
        }
        List<PlotLeveling.Box> boxes = new ArrayList<>();
        layers.forEach((y, rows) -> {
            List<PlotLeveling.Box> runs = new ArrayList<>();
            rows.forEach((z, xs) -> runs.addAll(runs(xs, y, z)));
            boxes.addAll(merged(runs));
        });
        return List.copyOf(boxes);
    }

    public static boolean fenceReady(StageSite site, ColumnMask.Cell post, IntBinaryOperator tops) {
        Objects.requireNonNull(post, "Столб ограды не может быть null: проверяется его клетка");
        Objects.requireNonNull(tops, "Высоты колонок не могут быть null: без них клетку столба не проверить");
        return tops.applyAsInt(post.x(), post.z()) <= fenceY(site);
    }

    public static boolean needsSupport(StageSite site, ColumnMask.Cell post, IntBinaryOperator tops) {
        Objects.requireNonNull(post, "Столб ограды не может быть null: проверяется его клетка");
        Objects.requireNonNull(tops, "Высоты колонок не могут быть null: без них опору под столбом не найти");
        return tops.applyAsInt(post.x(), post.z()) <= level(site);
    }

    public static List<ColumnMask.Cell> readyPosts(StageSite site, IntBinaryOperator tops) {
        return site.ring().posts().stream()
                .filter(post -> fenceReady(site, post, tops))
                .toList();
    }

    public static BlockCoord chestSpot(StageSite site) {
        int walk = fenceY(site);
        for (ColumnMask.Cell cell : site.level().cells()) {
            if (!site.planCell(cell.x(), walk, cell.z()) && !site.ringPost(cell.x(), cell.z())) {
                return new BlockCoord(cell.x(), walk, cell.z());
            }
        }
        return site.workYard();
    }

    private static boolean building(StageSite site, int x, int y, int z) {
        return site.planCell(x, y, z) && !site.ringPost(x, z);
    }

    private static List<PlotLeveling.Box> runs(List<Integer> xs, int y, int z) {
        List<Integer> sorted = xs.stream().sorted().toList();
        List<PlotLeveling.Box> found = new ArrayList<>();
        @Nullable Integer start = null;
        int last = 0;
        for (int x : sorted) {
            if (start == null) {
                start = x;
            } else if (x != last + 1) {
                found.add(new PlotLeveling.Box(start, y, z, last, y, z));
                start = x;
            }
            last = x;
        }
        if (start != null) {
            found.add(new PlotLeveling.Box(start, y, z, last, y, z));
        }
        return found;
    }

    private static List<PlotLeveling.Box> merged(List<PlotLeveling.Box> rows) {
        List<PlotLeveling.Box> sorted = rows.stream()
                .sorted(Comparator.comparingInt(PlotLeveling.Box::x1)
                        .thenComparingInt(PlotLeveling.Box::x2)
                        .thenComparingInt(PlotLeveling.Box::z1))
                .toList();
        List<PlotLeveling.Box> out = new ArrayList<>();
        PlotLeveling.Box open = null;
        for (PlotLeveling.Box row : sorted) {
            if (open != null && open.x1() == row.x1() && open.x2() == row.x2() && open.z2() + 1 == row.z1()) {
                open = new PlotLeveling.Box(open.x1(), open.y1(), open.z1(), open.x2(), open.y2(), row.z2());
                continue;
            }
            if (open != null) {
                out.add(open);
            }
            open = row;
        }
        if (open != null) {
            out.add(open);
        }
        return out;
    }
}

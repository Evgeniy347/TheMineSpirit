package com.theminespirit.core.board.route;

import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QuarryPlanner {
    public static final int LAYERS = 4;

    private QuarryPlanner() {}

    public static QuarryPlan plan(QuarryArea area, QuarryProbe probe) {
        Objects.requireNonNull(area, "Область добычи не может быть null: без неё нечего оценивать");
        Objects.requireNonNull(probe, "Мир области не может быть null: без него запас не проверить");
        ColumnMask mask = area.mask();
        int waterLevel = probe.waterLevel();
        int minX = mask.minX();
        int minZ = mask.minZ();
        int maxX = minX + mask.width() - 1;
        int maxZ = minZ + mask.depth() - 1;
        List<PlotLeveling.Box> cut = new ArrayList<>();
        int reserve = 0;
        int columns = 0;
        boolean waterLimited = false;
        for (ColumnMask.Cell cell : mask.cells()) {
            int x = cell.x();
            int z = cell.z();
            if (!probe.loaded(x, z)) {
                continue;
            }
            int top = probe.top(x, z);
            if (top == Integer.MIN_VALUE) {
                continue;
            }
            int ring = Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
            int depth = Math.min(LAYERS, ring + 1);
            int natural = top - depth;
            int target = Math.max(natural, waterLevel);
            if (waterLevel != QuarryProbe.NO_WATER && natural < waterLevel && top > waterLevel) {
                waterLimited = true;
            }
            if (target < 0) {
                target = 0;
            }
            int highest = Integer.MIN_VALUE;
            int lowest = Integer.MAX_VALUE;
            int column = 0;
            for (int y = top; y > target; y--) {
                if (probe.forbidden(x, y, z)) {
                    break;
                }
                if (!area.resource().matchesBlock(probe.block(x, y, z))) {
                    break;
                }
                if (probe.waterAround(x, y, z) > RouteDigging.MAX_WATER) {
                    waterLimited = true;
                    break;
                }
                column++;
                highest = Math.max(highest, y);
                lowest = Math.min(lowest, y);
            }
            if (column == 0) {
                continue;
            }
            reserve += column;
            columns++;
            cut.add(new PlotLeveling.Box(x, lowest, z, x, highest, z));
        }
        return new QuarryPlan(
                area, new PlotLeveling(cut, List.of(), PlotLeveling.DEFAULT_BLOCK), reserve, columns, waterLimited);
    }
}

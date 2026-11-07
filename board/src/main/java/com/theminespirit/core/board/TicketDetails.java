package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TicketDetails {
    private TicketDetails() {}

    static List<String> of(TicketTarget target) {
        List<String> details = new ArrayList<>();
        details.add(BoardActivities.TARGET_KEY + coords(target.target()));
        if (target.schemeFile() != null) {
            details.add("scheme=" + target.schemeFile());
        }
        if (target.building() != null) {
            details.add("building=" + target.building());
        }
        if (target.wood() != null) {
            details.add("wood=" + target.wood());
        }
        if (target.fenceBlock() != null) {
            details.add("fence=" + target.fenceBlock());
        }
        if (target.lureItem() != null) {
            details.add("lure=" + target.lureItem());
        }
        if (target.supplyItem() != null) {
            details.add("supply=" + target.supplyItem() + "×" + target.supplyCount());
        }
        if (target.craftItem() != null) {
            details.add("craft=" + target.craftItem() + "×" + target.count());
        }
        if (target.species() != null) {
            details.add("species=" + target.species().name().toLowerCase(Locale.ROOT));
        }
        if (target.from() != null) {
            details.add("from=" + coords(target.from()));
        }
        if (target.to() != null) {
            details.add("to=" + coords(target.to()));
        }
        if (target.deposit() != null) {
            details.add("deposit=" + coords(target.deposit()));
        }
        if (target.toolCount() > 0) {
            details.add("tools=" + target.toolCount());
        }
        if (target.craterBlocks() > 0) {
            PlotLeveling leveling = target.leveling();
            details.add(
                    leveling != null && !leveling.fill().isEmpty() ? pit(leveling) : "crater=" + target.craterBlocks());
        }
        if (!target.dropItems().isEmpty()) {
            details.add("drops=" + String.join(",", target.dropItems()));
        }
        return List.copyOf(details);
    }

    private static String pit(PlotLeveling leveling) {
        Map<Long, int[]> columns = new LinkedHashMap<>();
        for (PlotLeveling.Box box : leveling.fill()) {
            for (int x = box.x1(); x <= box.x2(); x++) {
                for (int z = box.z1(); z <= box.z2(); z++) {
                    long key = ((long) x << 32) ^ (z & 0xffffffffL);
                    int[] range = columns.computeIfAbsent(key, ignored -> new int[] {box.y1(), box.y2()});
                    range[0] = Math.min(range[0], box.y1());
                    range[1] = Math.max(range[1], box.y2());
                }
            }
        }
        int depth = columns.values().stream()
                .mapToInt(range -> range[1] - range[0] + 1)
                .max()
                .orElse(0);
        return "pit=" + columns.size() + " клеток, " + leveling.fillVolume() + " блоков, глубина " + depth;
    }

    private static String coords(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}

package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ConstructionMarks {
    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");

    public record Marks(Set<BlockCoord> demolish, Set<BlockCoord> place) {
        public Marks {
            demolish =
                    Set.copyOf(Objects.requireNonNull(demolish, "Набор на снос не может быть null: пустой — Set.of()"));
            place = Set.copyOf(
                    Objects.requireNonNull(place, "Набор к постановке не может быть null: пустой — Set.of()"));
        }
    }

    private ConstructionMarks() {}

    public static Marks of(RepairAudit.Report report, BlockCoord center, int radius) {
        Objects.requireNonNull(report, "Аудит ремонта не может быть null: без него нечего подсвечивать");
        Objects.requireNonNull(center, "Центр подсветки не может быть null: от него считается радиус");
        if (radius < 0) {
            throw new IllegalArgumentException(
                    "Радиус подсветки " + radius + ": расстояние не может быть отрицательным");
        }
        Set<BlockCoord> demolish = new LinkedHashSet<>();
        Set<BlockCoord> place = new LinkedHashSet<>();
        if (!report.rebuild()) {
            for (RepairAudit.Fix fix : report.fixes()) {
                if (!near(fix.cell(), center, radius)) {
                    continue;
                }
                if (fix.action() == RepairAudit.Action.CLEAR || !air(fix.found())) {
                    demolish.add(fix.cell());
                } else {
                    place.add(fix.cell());
                }
            }
        }
        return new Marks(demolish, place);
    }

    private static boolean near(BlockCoord cell, BlockCoord center, int radius) {
        return Math.abs(cell.x() - center.x()) <= radius
                && Math.abs(cell.y() - center.y()) <= radius
                && Math.abs(cell.z() - center.z()) <= radius;
    }

    private static boolean air(String block) {
        int colon = block.indexOf(':');
        String path = colon < 0 ? block : block.substring(colon + 1);
        return AIR.contains(path);
    }
}

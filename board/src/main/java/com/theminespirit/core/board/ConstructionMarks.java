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

    public static Marks in(RepairAudit.Report report, Set<ConstructionBatches.ChunkCell> chunks) {
        Objects.requireNonNull(report, "Аудит ремонта не может быть null: без него нечего подсвечивать");
        Objects.requireNonNull(chunks, "Набор чанков не может быть null: пустой — Set.of()");
        Set<BlockCoord> demolish = new LinkedHashSet<>();
        Set<BlockCoord> place = new LinkedHashSet<>();
        if (!report.rebuild()) {
            for (RepairAudit.Fix fix : report.fixes()) {
                if (!chunks.contains(
                        ConstructionBatches.at(fix.cell().x(), fix.cell().z()))) {
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

    private static boolean air(String block) {
        int colon = block.indexOf(':');
        String path = colon < 0 ? block : block.substring(colon + 1);
        return AIR.contains(path);
    }
}

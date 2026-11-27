package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.PlotCells;
import com.theminespirit.core.town.TownPlan;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

final class GroundEnvelopes {
    private GroundEnvelopes() {}

    static void collect(TownPlan plan, int stage, int levelY, List<Expectation> out) {
        Objects.requireNonNull(plan, "План не может быть null: земля берётся из его контура");
        Objects.requireNonNull(out, "Список ожиданий не может быть null: в него пишутся клетки");
        ColumnMask region = plan.region();
        BitSet lots = lotColumns(plan, stage, region);
        for (ColumnMask.Cell cell : region.cells()) {
            if (plan.streets().contains(cell.x(), cell.z())) {
                continue;
            }
            BlockCoord ground = new BlockCoord(cell.x(), levelY - 1, cell.z());
            out.add(new Expectation(ground, Wanted.ground(levelY - 1), SourceRef.GROUND, Access.SPIRIT));
            if (lots.get(index(cell, region))) {
                continue;
            }
            for (int dy = 0; dy < PlotCells.FREE_HEIGHT; dy++) {
                out.add(new Expectation(
                        new BlockCoord(cell.x(), levelY + dy, cell.z()),
                        Wanted.air(),
                        SourceRef.GROUND,
                        Access.SPIRIT));
            }
        }
    }

    private static BitSet lotColumns(TownPlan plan, int stage, ColumnMask region) {
        BitSet lots = new BitSet(region.width() * region.depth());
        for (Placement place : plan.upToStage(stage)) {
            for (int x = place.x(); x < place.x() + place.width(); x++) {
                for (int z = place.z(); z < place.z() + place.depth(); z++) {
                    ColumnMask.Cell cell = new ColumnMask.Cell(x, z);
                    if (region.contains(x, z)) {
                        lots.set(index(cell, region));
                    }
                }
            }
        }
        return lots;
    }

    private static int index(ColumnMask.Cell cell, ColumnMask region) {
        return (cell.z() - region.minZ()) * region.width() + cell.x() - region.minX();
    }
}

package com.theminespirit.core.board.audit;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotCells;
import com.theminespirit.core.town.TownMapRules;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class StreetEnvelopes {
    private StreetEnvelopes() {}

    static void collect(TownPlan plan, int levelY, List<Expectation> out) {
        Objects.requireNonNull(plan, "План не может быть null: улицы берутся из него");
        Objects.requireNonNull(out, "Список ожиданий не может быть null: в него пишутся клетки");
        TownStreets streets = plan.streets();
        List<ColumnMask.Cell> cells = new ArrayList<>(streets.main().cells());
        cells.addAll(streets.side().cells());
        for (ColumnMask.Cell cell : cells) {
            SourceRef source = SourceRef.street(streets.idAt(cell.x(), cell.z()).orElse(TownMapRules.MAIN));
            out.add(new Expectation(
                    new BlockCoord(cell.x(), levelY - 1, cell.z()), Wanted.ground(levelY - 1), source, Access.SPIRIT));
            for (int dy = 0; dy < PlotCells.FREE_HEIGHT; dy++) {
                out.add(new Expectation(
                        new BlockCoord(cell.x(), levelY + dy, cell.z()), Wanted.air(), source, Access.SPIRIT));
            }
        }
    }
}

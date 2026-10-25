package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineColumnTests {
    private static final BlockCoord ANCHOR = new BlockCoord(-171, 70, -484);

    @Test
    void mineColumn_EveryDescentCell_InsideColumn() {
        Preset preset = DataTables.INSTANCE
                .presets()
                .current()
                .byId(TicketPlans.MINE_DESCENT_SCHEME)
                .orElseThrow();
        MineLayout layout = MineLayout.place(
                preset.schematic(),
                preset.entry(),
                TicketPlans.mineDescentOrigin(ANCHOR),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE);
        List<BlockCoord> cells = new ArrayList<>(layout.floors());
        cells.addAll(layout.air());
        layout.optional().forEach(entry -> cells.add(entry.pos()));
        int shallow = ANCHOR.y() - 1 - PlotLeveling.MAX_SHIFT;
        List<BlockCoord> outside = cells.stream()
                .filter(cell -> cell.y() >= shallow)
                .filter(cell -> !SettlementLayout.mineColumn(ANCHOR, cell.x(), cell.z()))
                .toList();
        assertEquals(
                List.of(),
                outside,
                "Клетки спуска в шахту у поверхности вне колонны шахты: выравнивание, верхний слой земли и аудит "
                        + "ровности засыпают и срезают их ступени — спуск ломается, «к ступени не подойти» (INC-81)");
    }
}

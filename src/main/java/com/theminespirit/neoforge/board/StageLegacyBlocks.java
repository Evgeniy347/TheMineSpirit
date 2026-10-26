package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class StageLegacyBlocks {
    private StageLegacyBlocks() {}

    static List<PlannedBlock> grove(ServerLevel level, StageSite site) {
        String wood =
                SettlementPlotStore.get(level).plot().map(plot -> plot.wood()).orElseThrow();
        List<PlannedBlock> blocks = new ArrayList<>();
        for (BlockCoord spot : site.saplings()) {
            int y = GroundProbe.feet(level, spot.x(), spot.z(), site.anchor().y());
            blocks.add(new PlannedBlock(new BlockCoord(spot.x(), y, spot.z()), StagePlans.sapling(wood)));
        }
        return List.copyOf(blocks);
    }
}

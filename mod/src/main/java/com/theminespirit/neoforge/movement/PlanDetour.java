package com.theminespirit.neoforge.movement;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlanBuildings;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BuildPlanOwner;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class PlanDetour {
    private PlanDetour() {}

    public static boolean foreign(SpiritEntity spirit, BlockCoord cell) {
        if (!(spirit.level() instanceof ServerLevel server)) {
            return false;
        }
        Optional<StageSite> found = SettlementPlotStore.liveSite(server);
        if (found.isEmpty()) {
            return false;
        }
        StageSite site = found.orElseThrow();
        if (!site.planCell(cell.x(), cell.y(), cell.z())) {
            return false;
        }
        PlanBuilding own = BuildPlanOwner.of(spirit);
        PlanBuilding here = site.planBuilding(cell.x(), cell.y(), cell.z());
        if (here == PlanBuilding.NONE || here == PlanBuilding.MINE) {
            return false;
        }
        return PlanBuildings.foreign(own, here);
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.ui.MineBlocks;
import com.theminespirit.core.ui.MineGeometry;
import com.theminespirit.core.ui.MineSnapshot;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class MineSnapshots {
    private MineSnapshots() {}

    public static Optional<MineSnapshot> snapshot(ServerLevel level, int y) {
        return geometry(level).map(found -> MineSnapshot.of(found, y, new WorldBlocks(level)));
    }

    public static Optional<MineGeometry> geometry(ServerLevel level) {
        return layout(level).map(found -> MineGeometry.of(found, TicketPlans.MINE_TUNNEL_SIZE));
    }

    private static Optional<MineLayout> layout(ServerLevel level) {
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(TicketPlans.MINE_DESCENT_SCHEME);
        if (site.isEmpty() || preset.isEmpty()) {
            return Optional.empty();
        }
        StageSite stage = site.orElseThrow();
        Preset descent = preset.orElseThrow();
        return Optional.of(MineLayout.place(
                descent.schematic(),
                descent.entry(),
                stage.mineEntrance(),
                BuildRotation.parse(TicketPlans.MINE_DESCENT_ROTATION),
                MineLayout.Extent.NONE));
    }

    private record WorldBlocks(ServerLevel level) implements MineBlocks {
        @Override
        public Observed at(BlockCoord cell) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!level.isLoaded(pos)) {
                return new Observed("minecraft:stone", false);
            }
            BlockState state = level.getBlockState(pos);
            boolean empty = state.getCollisionShape(level, pos).isEmpty();
            return new Observed(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), empty);
        }
    }
}

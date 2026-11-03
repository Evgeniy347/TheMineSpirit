package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class RepairAuditWatch {
    private static final Map<BlockCoord, Long> FIRST_SEEN = new HashMap<>();

    private RepairAuditWatch() {}

    static RepairAudit.@Nullable Report houseReport(ServerLevel level, StageSite site, long now) {
        Optional<String> scheme = DataTables.INSTANCE.stages().current().scheme(BoardCase.BUILD_HOUSE, 1);
        if (scheme.isEmpty()) {
            return null;
        }
        return RepairAudit.audit(
                SchematicResources.load(scheme.orElseThrow()),
                site.houseOrigin(),
                BuildRotation.NONE,
                new LevelWorld(level),
                RepairData.loadDefault(),
                FIRST_SEEN,
                now);
    }

    private record LevelWorld(ServerLevel level) implements RepairAudit.WorldView {
        @Override
        public String blockAt(BlockCoord cell) {
            return BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .getBlock())
                    .toString();
        }

        @Override
        public boolean playerPlaced(BlockCoord cell) {
            return PlacedBlocksData.get(level).isMarked(new BlockPos(cell.x(), cell.y(), cell.z()));
        }

        @Override
        public boolean loaded(BlockCoord cell) {
            return level.isLoaded(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

final class ChestSnapshots {
    private ChestSnapshots() {}

    static Optional<ColumnMask> area(ServerLevel level) {
        Optional<ColumnMask> town = SettlementPlotStore.get(level).town().map(TownPlan::region);
        if (town.isPresent()) {
            return town;
        }
        return SettlementPlotStore.liveSite(level).map(StageSite::enclosure);
    }

    static List<BlockCoord> positions(ServerLevel level) {
        Set<BlockCoord> found = new LinkedHashSet<>();
        Optional<ColumnMask> area = area(level);
        for (BlockCoord pos : SettlementChestStore.get(level).chests().all()) {
            if (area.map(mask -> mask.contains(pos.x(), pos.z())).orElse(true)) {
                found.add(pos);
            }
        }
        area.ifPresent(mask -> found.addAll(inArea(level, mask)));
        return List.copyOf(found);
    }

    static List<ContainerSnapshot> read(ServerLevel level, List<BlockCoord> positions) {
        List<ContainerSnapshot> snapshots = new ArrayList<>();
        for (BlockCoord pos : positions) {
            ContainerSnapshot snapshot = snapshot(level, pos);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return List.copyOf(snapshots);
    }

    static boolean countable(ServerLevel level, BlockCoord pos) {
        BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        if (!ContainerAccess.allowed(state)) {
            return false;
        }
        return !(state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT);
    }

    static @Nullable ContainerSnapshot snapshot(ServerLevel level, BlockCoord pos) {
        if (!countable(level, pos)) {
            return null;
        }
        BlockPos at = new BlockPos(pos.x(), pos.y(), pos.z());
        BlockState state = level.getBlockState(at);
        Container container = ownIfPartnerUnloaded(level, at, state);
        if (container == null) {
            ContainerAccess.Open open = ContainerAccess.open(level, at);
            if (!(open instanceof ContainerAccess.Opened opened)) {
                return null;
            }
            container = opened.container();
        }
        return new ContainerSnapshot(pos, new StackMapper().map(container));
    }

    private static @Nullable Container ownIfPartnerUnloaded(ServerLevel level, BlockPos at, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos partner = at.relative(ChestBlock.getConnectedDirection(state));
        if (level.isLoaded(partner)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(at);
        return entity instanceof BaseContainerBlockEntity own ? own : null;
    }

    static List<BlockCoord> inArea(ServerLevel level, ColumnMask area) {
        int minChunkX = area.minX() >> 4;
        int minChunkZ = area.minZ() >> 4;
        int maxChunkX = (area.minX() + area.width() - 1) >> 4;
        int maxChunkZ = (area.minZ() + area.depth() - 1) >> 4;
        List<BlockCoord> found = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos at = entity.getBlockPos();
                    if (!area.contains(at.getX(), at.getZ()) || !ContainerAccess.allowed(entity.getBlockState())) {
                        continue;
                    }
                    found.add(new BlockCoord(at.getX(), at.getY(), at.getZ()));
                }
            }
        }
        return found;
    }
}

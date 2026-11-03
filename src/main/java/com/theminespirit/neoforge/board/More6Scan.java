package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.More6Rules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class More6Scan {
    private static final int BED_RADIUS = 20;
    private static final int BED_VERTICAL = 6;
    private static final int LOOSE_RADIUS = 24;
    private static final int WATER_BELOW = 4;
    private static final int WATER_ABOVE = 4;
    private static final int STATION_RADIUS = 8;
    private static final int STATION_DOWN = 3;
    private static final int STATION_UP = 2;
    private static final int FUEL_SLOT = 1;

    record StationScan(int unfuelled, int misplaced) {}

    private More6Scan() {}

    static int beds(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int placed = 0;
        for (int dx = -BED_RADIUS; dx <= BED_RADIUS; dx++) {
            for (int dz = -BED_RADIUS; dz <= BED_RADIUS; dz++) {
                for (int dy = -BED_VERTICAL; dy <= BED_VERTICAL; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof BedBlock) {
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    static int looseItems(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(LOOSE_RADIUS)).stream()
                .filter(item -> !item.isRemoved() && !item.getItem().isEmpty())
                .toList()
                .size();
    }

    static int dryFields(ServerLevel level, List<GraphFacts.Position> fields) {
        int dry = 0;
        for (GraphFacts.Position field : fields) {
            if (!waterNear(level, field)) {
                dry++;
            }
        }
        return dry;
    }

    static StationScan stations(ServerLevel level, StageSite stage, Set<String> builtStations) {
        BlockCoord yard = stage.workYard();
        BlockPos center = new BlockPos(yard.x(), yard.y(), yard.z());
        Set<String> found = new HashSet<>();
        int unfuelled = 0;
        for (int dx = -STATION_RADIUS; dx <= STATION_RADIUS; dx++) {
            for (int dz = -STATION_RADIUS; dz <= STATION_RADIUS; dz++) {
                for (int dy = -STATION_DOWN; dy <= STATION_UP; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER)) {
                        String type = state.is(Blocks.FURNACE) ? "furnace" : "smoker";
                        found.add(type);
                        if (emptyFuel(level, at)) {
                            unfuelled++;
                        }
                    } else if (state.is(Blocks.CRAFTING_TABLE)) {
                        found.add("crafting_table");
                    }
                }
            }
        }
        int misplaced = 0;
        for (String station : builtStations) {
            if (!found.contains(station)) {
                misplaced++;
            }
        }
        return new StationScan(unfuelled, misplaced);
    }

    private static boolean waterNear(ServerLevel level, GraphFacts.Position field) {
        BlockPos center = new BlockPos(field.x(), field.y(), field.z());
        for (int dx = -More6Rules.WATER_REACH; dx <= More6Rules.WATER_REACH; dx++) {
            for (int dz = -More6Rules.WATER_REACH; dz <= More6Rules.WATER_REACH; dz++) {
                for (int dy = -WATER_BELOW; dy <= WATER_ABOVE; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (level.isLoaded(at)
                            && level.getFluidState(at).isSource()
                            && level.getFluidState(at).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean emptyFuel(ServerLevel level, BlockPos at) {
        BlockEntity entity = level.getBlockEntity(at);
        if (entity instanceof Container container && container.getContainerSize() > FUEL_SLOT) {
            return container.getItem(FUEL_SLOT).isEmpty();
        }
        return false;
    }
}

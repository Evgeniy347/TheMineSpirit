package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.board.route.RouteSpot;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class BiomeSpots {
    private static final int RADIUS = 96;
    private static final int STEP = 16;
    private static final int SOURCE_RADIUS = 2;

    private BiomeSpots() {}

    static List<RouteSpot> scan(ServerLevel level, BlockCoord home) {
        List<RouteSpot> spots = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                int x = home.x() + dx;
                int z = home.z() + dz;
                BlockPos at = new BlockPos(x, home.y(), z);
                if (!level.isLoaded(at)) {
                    continue;
                }
                String biome = level.getBiome(at)
                        .unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("minecraft:plains");
                Optional<RouteResource> resource = RouteResource.ofBiome(biome);
                if (resource.isEmpty()) {
                    continue;
                }
                int feet = GroundProbe.feet(level, x, z, home.y());
                if (sourceAt(level, new BlockCoord(x, feet, z), resource.orElseThrow())) {
                    spots.add(new RouteSpot(resource.orElseThrow(), biome, new BlockCoord(x, feet, z)));
                }
            }
        }
        return List.copyOf(spots);
    }

    static boolean sourceAt(ServerLevel level, BlockCoord point, RouteResource resource) {
        for (int dx = -SOURCE_RADIUS; dx <= SOURCE_RADIUS; dx++) {
            for (int dz = -SOURCE_RADIUS; dz <= SOURCE_RADIUS; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    BlockPos at = new BlockPos(point.x() + dx, point.y() + dy, point.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    String block =
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (resource.matchesBlock(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

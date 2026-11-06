package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.HiveRules;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;

final class HiveSite {
    private static final int HIVE_VERTICAL_RADIUS = 8;
    private static final int FLOWER_VERTICAL_RADIUS = 4;

    private HiveSite() {}

    static Optional<BlockCoord> harvest(ServerLevel level, BlockCoord home) {
        return nearestFullHive(level, home).map(HiveSite::coord);
    }

    static boolean harvestable(StockView stock) {
        return HiveRules.chooseTool(
                        stock.available().getOrDefault(HiveRules.SHEARS, 0) > 0,
                        stock.available().getOrDefault(HiveRules.GLASS_BOTTLE, 0))
                != HiveRules.Tool.NONE;
    }

    static Map<String, Integer> harvestNeed(StockView stock) {
        HiveRules.Tool tool = HiveRules.chooseTool(
                stock.available().getOrDefault(HiveRules.SHEARS, 0) > 0,
                stock.available().getOrDefault(HiveRules.GLASS_BOTTLE, 0));
        return tool == HiveRules.Tool.SHEARS ? Map.of(HiveRules.SHEARS, 1) : Map.of(HiveRules.GLASS_BOTTLE, 1);
    }

    static boolean hiveNear(ServerLevel level, BlockCoord home) {
        return hiveNear(level, home, HiveRules.siteRadius());
    }

    static Optional<BlockCoord> place(ServerLevel level, BlockCoord home) {
        return flowerSite(level, home).map(HiveSite::coord);
    }

    static Optional<Map<String, Integer>> placeNeed(StockView stock) {
        if (!HiveRules.honeycombEnough(stock.available().getOrDefault(HiveRules.HONEYCOMB, 0))) {
            return Optional.empty();
        }
        Optional<String> planks = planksType(stock);
        if (planks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                Map.of(HiveRules.HONEYCOMB, HiveRules.hiveHoneycomb(), planks.orElseThrow(), HiveRules.hivePlanks()));
    }

    private static Optional<BlockPos> nearestFullHive(ServerLevel level, BlockCoord home) {
        int radius = HiveRules.hiveRadius();
        BlockPos origin = new BlockPos(home.x(), home.y(), home.z());
        BlockPos found = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -HIVE_VERTICAL_RADIUS, -radius),
                origin.offset(radius, HIVE_VERTICAL_RADIUS, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BeehiveBlock)
                    || !state.hasProperty(BeehiveBlock.HONEY_LEVEL)
                    || !HiveRules.fullHive(state.getValue(BeehiveBlock.HONEY_LEVEL))) {
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < best) {
                best = distance;
                found = pos.immutable();
            }
        }
        return Optional.ofNullable(found);
    }

    private static Optional<BlockPos> flowerSite(ServerLevel level, BlockCoord home) {
        List<BlockPos> flowers = flowersNear(level, home);
        if (flowers.isEmpty()) {
            return Optional.empty();
        }
        int flowerRadius = HiveRules.flowerRadius();
        for (BlockPos flower : flowers) {
            BlockPos ground = flower.below();
            for (int dx = -flowerRadius; dx <= flowerRadius; dx++) {
                for (int dz = -flowerRadius; dz <= flowerRadius; dz++) {
                    BlockPos support = ground.offset(dx, 0, dz);
                    long shiftX = (long) support.getX() - home.x();
                    long shiftZ = (long) support.getZ() - home.z();
                    if (shiftX * shiftX + shiftZ * shiftZ > (long) HiveRules.siteRadius() * HiveRules.siteRadius()) {
                        continue;
                    }
                    if (!supports(level, support.above())) {
                        continue;
                    }
                    if (flowersAround(support.above(), flowers, flowerRadius) < HiveRules.flowersMin()) {
                        continue;
                    }
                    return Optional.of(support);
                }
            }
        }
        return Optional.empty();
    }

    private static List<BlockPos> flowersNear(ServerLevel level, BlockCoord home) {
        int radius = HiveRules.siteRadius();
        BlockPos origin = new BlockPos(home.x(), home.y(), home.z());
        List<BlockPos> flowers = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -FLOWER_VERTICAL_RADIUS, -radius),
                origin.offset(radius, FLOWER_VERTICAL_RADIUS, radius))) {
            if (level.getBlockState(pos).is(BlockTags.FLOWERS)) {
                flowers.add(pos.immutable());
            }
        }
        flowers.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        return flowers;
    }

    private static long flowersAround(BlockPos center, List<BlockPos> known, int radius) {
        long count = 0;
        for (BlockPos flower : known) {
            long dx = (long) flower.getX() - center.getX();
            long dz = (long) flower.getZ() - center.getZ();
            if (dx * dx + dz * dz <= (long) radius * radius) {
                count++;
            }
        }
        return count;
    }

    private static boolean hiveNear(ServerLevel level, BlockCoord home, int radius) {
        BlockPos origin = new BlockPos(home.x(), home.y(), home.z());
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-radius, -HIVE_VERTICAL_RADIUS, -radius),
                origin.offset(radius, HIVE_VERTICAL_RADIUS, radius))) {
            if (level.getBlockState(pos).getBlock() instanceof BeehiveBlock) {
                return true;
            }
        }
        return false;
    }

    private static boolean supports(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        if (!state.isAir() && !state.canBeReplaced()) {
            return false;
        }
        BlockPos below = cell.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static Optional<String> planksType(StockView stock) {
        List<String> types = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stock.available().entrySet()) {
            if (entry.getKey().endsWith("_planks") && HiveRules.planksEnough(entry.getValue())) {
                types.add(entry.getKey());
            }
        }
        types.sort(String::compareTo);
        return types.stream().findFirst();
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}

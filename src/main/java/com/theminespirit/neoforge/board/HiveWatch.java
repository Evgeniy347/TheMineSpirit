package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.HiveRules;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
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

/**
 * Источник доски «пчёлы»: полный улей рядом с поселением — задание собрать соты или мёд;
 * соты и доски в запасах, а рядом с домом цветы — задание поставить улей. Пчёл не убивают
 * ни один из планов: сбор идёт только через ножницы или бутылку.
 */
public final class HiveWatch {
    public static final String CAUSE = "hive:settlement";

    private static final String HARVEST_ID = "hive_harvest";
    private static final String PLACE_ID = "hive_beehive";
    private static final int HIVE_VERTICAL_RADIUS = 8;
    private static final int FLOWER_VERTICAL_RADIUS = 4;

    private long lastScan = -1;
    private boolean gate;

    public boolean live() {
        return gate;
    }

    public void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        gate = SpiritConfig.boardEnabled() && !spirits.isEmpty();
        if (!gate) {
            return;
        }
        if (now - lastScan < BoardTuning.herdScanIntervalTicks()) {
            return;
        }
        lastScan = now;
        BlockCoord home = homeOf(spirits);
        ensureHarvest(level, service, stock, home, now);
        ensurePlace(level, service, stock, home, now);
    }

    private static void ensureHarvest(
            ServerLevel level, BoardService service, StockView stock, BlockCoord home, long now) {
        if (active(service, HARVEST_ID)) {
            return;
        }
        Optional<BlockPos> hive = nearestFullHive(level, home);
        if (hive.isEmpty()) {
            return;
        }
        HiveRules.Tool tool = HiveRules.chooseTool(
                stock.available().getOrDefault(HiveRules.SHEARS, 0) > 0,
                stock.available().getOrDefault(HiveRules.GLASS_BOTTLE, 0));
        if (tool == HiveRules.Tool.NONE) {
            return;
        }
        Map<String, Integer> required =
                tool == HiveRules.Tool.SHEARS ? Map.of(HiveRules.SHEARS, 1) : Map.of(HiveRules.GLASS_BOTTLE, 1);
        submit(service, HARVEST_ID, BoardCase.HARVEST_HIVE, now, coord(hive.orElseThrow()), required);
    }

    private static void ensurePlace(
            ServerLevel level, BoardService service, StockView stock, BlockCoord home, long now) {
        if (active(service, PLACE_ID)) {
            return;
        }
        if (!HiveRules.honeycombEnough(stock.available().getOrDefault(HiveRules.HONEYCOMB, 0))) {
            return;
        }
        Optional<String> planks = planksType(stock);
        if (planks.isEmpty()) {
            return;
        }
        if (hiveNear(level, home, HiveRules.siteRadius())) {
            return;
        }
        Optional<BlockPos> site = flowerSite(level, home);
        if (site.isEmpty()) {
            return;
        }
        Map<String, Integer> required =
                Map.of(HiveRules.HONEYCOMB, HiveRules.hiveHoneycomb(), planks.orElseThrow(), HiveRules.hivePlanks());
        submit(service, PLACE_ID, BoardCase.PLACE_BEEHIVE, now, coord(site.orElseThrow()), required);
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

    private static void submit(
            BoardService service,
            String id,
            BoardCase boardCase,
            long now,
            BlockCoord target,
            Map<String, Integer> required) {
        String cause = boardCase == BoardCase.HARVEST_HIVE ? CAUSE + ":harvest" : CAUSE + ":place";
        service.submit(
                new BoardTicket(
                        id,
                        boardCase,
                        Optional.empty(),
                        BoardRules.priorityOf(boardCase),
                        now,
                        1,
                        cause,
                        List.of(),
                        required),
                TicketTarget.at(target));
    }

    private static boolean active(BoardService service, String id) {
        return service.board()
                .statusOf(id)
                .map(status -> status == BoardStatus.PENDING
                        || status == BoardStatus.WAITING_RESOURCES
                        || status == BoardStatus.ASSIGNED)
                .orElse(false);
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}

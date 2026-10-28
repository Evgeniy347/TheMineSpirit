package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class GraphSurroundings {
    static final String DROPS = "drops_nearby";
    static final String LEAF_DECAY = "leaf_decay";
    private static final int RADIUS = 32;
    private static final int STEP = 1;
    private static final int BELOW = 4;
    private static final int ABOVE = 12;
    private static final int DROP_RADIUS = 24;
    private static final String LOG = "_log";
    private static final Set<String> RESOURCES = Set.of("sand", "red_sand", "clay");

    private GraphSurroundings() {}

    record Around(
            Set<String> world,
            Set<String> events,
            Map<String, BlockCoord> drops,
            Optional<BlockCoord> decay,
            Map<String, BlockCoord> nearest) {
        Around with(Set<String> more) {
            Set<String> all = new HashSet<>(events);
            all.addAll(more);
            return new Around(world, Set.copyOf(all), drops, decay, nearest);
        }

        Optional<BlockCoord> resource(String world) {
            return Optional.ofNullable(nearest.get(world));
        }

        Optional<BlockCoord> drop(String item) {
            return Optional.ofNullable(drops.get(item));
        }

        Optional<BlockCoord> anyDrop() {
            return drops.values().stream().findFirst();
        }
    }

    private record Scan(Set<String> blocks, Optional<BlockCoord> decay, Map<String, BlockCoord> nearest) {}

    static Around of(ServerLevel level, BoardService service, BlockCoord home) {
        Scan scan = blocks(level, home);
        AABB box = new AABB(new BlockPos(home.x(), home.y(), home.z())).inflate(RADIUS);
        Set<String> creatures = new HashSet<>();
        if (!level.getEntitiesOfClass(Animal.class, box).isEmpty()) {
            creatures.add("animals");
        }
        if (!level.getEntitiesOfClass(Squid.class, box).isEmpty()) {
            creatures.add("squid");
        }
        if (!level.getEntitiesOfClass(Monster.class, box).isEmpty()) {
            creatures.add("mobs");
        }
        level.getEntitiesOfClass(Sheep.class, box)
                .forEach(sheep -> creatures.add("sheep:" + sheep.getColor().getName()));
        Map<String, BlockCoord> drops = drops(level, service, home);
        Set<String> events = new HashSet<>();
        if (!drops.isEmpty()) {
            events.add(DROPS);
        }
        scan.decay().ifPresent(at -> events.add(LEAF_DECAY));
        return new Around(
                GraphNames.world(scan.blocks(), creatures), Set.copyOf(events), drops, scan.decay(), scan.nearest());
    }

    private static Map<String, BlockCoord> drops(ServerLevel level, BoardService service, BlockCoord home) {
        boolean collecting = service.board().all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.COLLECT_DROPS)
                .anyMatch(ticket -> service.board()
                        .statusOf(ticket.id())
                        .filter(status -> status == BoardStatus.PENDING
                                || status == BoardStatus.WAITING_RESOURCES
                                || status == BoardStatus.ASSIGNED)
                        .isPresent());
        if (collecting) {
            return Map.of();
        }
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        Map<String, BlockCoord> found = new HashMap<>();
        Map<String, Double> distances = new HashMap<>();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(DROP_RADIUS))) {
            if (drop.isRemoved()) {
                continue;
            }
            String item =
                    BuiltInRegistries.ITEM.getKey(drop.getItem().getItem()).getPath();
            double distance = drop.blockPosition().distSqr(center);
            if (distance < distances.getOrDefault(item, Double.MAX_VALUE)) {
                distances.put(item, distance);
                found.put(item, new BlockCoord(drop.getBlockX(), drop.getBlockY(), drop.getBlockZ()));
            }
        }
        return Map.copyOf(found);
    }

    private static Scan blocks(ServerLevel level, BlockCoord home) {
        Set<String> blocks = new HashSet<>();
        BlockCoord decay = null;
        Map<String, BlockCoord> nearest = new HashMap<>();
        Map<String, Integer> distances = new HashMap<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
            for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    BlockPos pos = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    blocks.add(id);
                    if (id.endsWith(LOG) || RESOURCES.contains(id)) {
                        int distance = dx * dx + dy * dy + dz * dz;
                        for (String tag : GraphNames.world(Set.of(id), Set.of())) {
                            if (distance < distances.getOrDefault(tag, Integer.MAX_VALUE)) {
                                distances.put(tag, distance);
                                nearest.put(tag, new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
                            }
                        }
                    }
                    if (decay == null && decaying(state)) {
                        decay = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
                    }
                }
            }
        }
        return new Scan(Set.copyOf(blocks), Optional.ofNullable(decay), Map.copyOf(nearest));
    }

    private static boolean decaying(BlockState state) {
        return state.getBlock() instanceof LeavesBlock
                && !state.getValue(LeavesBlock.PERSISTENT)
                && state.getValue(LeavesBlock.DISTANCE) == LeavesBlock.DECAY_DISTANCE;
    }
}

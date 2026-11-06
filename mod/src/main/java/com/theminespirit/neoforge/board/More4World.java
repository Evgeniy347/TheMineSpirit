package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More4Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

final class More4World {
    private static final int PLAYER_RADIUS = 24;
    private static final int PORTAL_RADIUS = 24;
    private static final int PORTAL_BELOW = 10;
    private static final int PORTAL_ABOVE = 10;
    private static final int HAZARD_RADIUS = 4;
    private static final int PET_RADIUS = 32;
    private static final int SPACE_RADIUS = 48;
    private static final int SPACE_STEP = 4;
    private static final int SPACE_EDGE = 2;

    private More4World() {}

    static More4Rules.Signals signals(
            GraphState state,
            ServerLevel level,
            List<SpiritEntity> spirits,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph) {
        GraphFacts facts = state.facts();
        return new More4Rules.Signals(
                players(level, spirits, scan.home()),
                portal(level, scan.home()),
                More4Rules.wrongHands(hands(spirits, graph)),
                lots(level, scan),
                pets(level, scan.home()),
                rare(facts, graph),
                settlement(facts));
    }

    private static More4Rules.Players players(ServerLevel level, List<SpiritEntity> spirits, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        long limit = (long) PLAYER_RADIUS * PLAYER_RADIUS;
        int near = 0;
        int hungry = 0;
        int broken = 0;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !near(player, spirits, center, limit)) {
                continue;
            }
            near++;
            if (player.getFoodData().getFoodLevel() <= More4Rules.HUNGRY_FOOD) {
                hungry++;
            }
            ItemStack hand = player.getMainHandItem();
            if (!hand.isEmpty() && hand.getMaxDamage() > 0 && hand.getDamageValue() >= hand.getMaxDamage() - 1) {
                broken++;
            }
        }
        return new More4Rules.Players(near, hungry, broken);
    }

    private static boolean near(ServerPlayer player, List<SpiritEntity> spirits, BlockPos home, long limit) {
        BlockPos at = player.blockPosition();
        if (spirits.isEmpty()) {
            return home.distSqr(at) <= limit;
        }
        return spirits.stream().anyMatch(spirit -> spirit.blockPosition().distSqr(at) <= limit);
    }

    private static More4Rules.Portal portal(ServerLevel level, BlockCoord home) {
        return portalPoint(level, home)
                .map(at -> new BlockPos(at.x(), at.y(), at.z()))
                .map(at -> new More4Rules.Portal(true, true, enclosed(level, at), hazard(level, at)))
                .orElseGet(() -> new More4Rules.Portal(false, false, false, false));
    }

    static Optional<BlockCoord> portalPoint(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        for (int dx = -PORTAL_RADIUS; dx <= PORTAL_RADIUS; dx++) {
            for (int dz = -PORTAL_RADIUS; dz <= PORTAL_RADIUS; dz++) {
                for (int dy = -PORTAL_BELOW; dy <= PORTAL_ABOVE; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).is(Blocks.NETHER_PORTAL)) {
                        return Optional.of(new BlockCoord(at.getX(), at.getY(), at.getZ()));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean enclosed(ServerLevel level, BlockPos at) {
        int sides = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : directions) {
            if (solid(level, at.offset(direction[0], 0, direction[1]))
                    && solid(level, at.offset(direction[0], 1, direction[1]))) {
                sides++;
            }
        }
        return sides >= 3;
    }

    private static boolean solid(ServerLevel level, BlockPos at) {
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(Blocks.NETHER_PORTAL);
    }

    private static boolean hazard(ServerLevel level, BlockPos at) {
        for (int dx = -HAZARD_RADIUS; dx <= HAZARD_RADIUS; dx++) {
            for (int dy = -HAZARD_RADIUS; dy <= HAZARD_RADIUS; dy++) {
                for (int dz = -HAZARD_RADIUS; dz <= HAZARD_RADIUS; dz++) {
                    BlockPos pos = at.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<More4Rules.Hand> hands(List<SpiritEntity> spirits, SupplyGraph graph) {
        List<More4Rules.Hand> hands = new ArrayList<>(spirits.size());
        for (SpiritEntity spirit : spirits) {
            String required = More4Rules.requiredKind(spirit.currentActivity().orElse(null));
            if (required == null) {
                continue;
            }
            List<String> items = items(spirit);
            List<String> hand = new ArrayList<>(1);
            ItemStack main = spirit.getMainHandItem();
            if (!main.isEmpty()) {
                hand.add(BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
            }
            Map<String, Integer> held = GraphKits.of(hand, graph);
            String heldKind = held.keySet().stream().findFirst().orElse(null);
            boolean owns = GraphKits.of(items, graph).containsKey(required);
            hands.add(new More4Rules.Hand(required, heldKind, owns));
        }
        return hands;
    }

    private static List<String> items(SpiritEntity spirit) {
        List<String> items = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        ItemStack main = spirit.getMainHandItem();
        if (!main.isEmpty()) {
            items.add(BuiltInRegistries.ITEM.getKey(main.getItem()).toString());
        }
        return items;
    }

    private static More4Rules.Lots lots(ServerLevel level, FirstStepsRunner.StageScan scan) {
        BlockCoord home = scan.home();
        ColumnMask enclosure = scan.stage().enclosure();
        int free = 0;
        for (int dx = -SPACE_RADIUS; dx <= SPACE_RADIUS; dx += SPACE_STEP) {
            for (int dz = -SPACE_RADIUS; dz <= SPACE_RADIUS; dz += SPACE_STEP) {
                int x = home.x() + dx;
                int z = home.z() + dz;
                if (enclosure.contains(x, z) || !level.isLoaded(new BlockPos(x, home.y(), z))) {
                    continue;
                }
                if (flat(level, x, z)) {
                    free++;
                }
            }
        }
        return new More4Rules.Lots(true, free);
    }

    private static boolean flat(ServerLevel level, int x, int z) {
        int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockState ground = level.getBlockState(new BlockPos(x, height - 1, z));
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }
        int size = (2 * SPACE_EDGE + 1) * (2 * SPACE_EDGE + 1);
        int[] neighbours = new int[size];
        boolean[] loaded = new boolean[size];
        int index = 0;
        for (int dx = -SPACE_EDGE; dx <= SPACE_EDGE; dx++) {
            for (int dz = -SPACE_EDGE; dz <= SPACE_EDGE; dz++) {
                BlockPos probe = new BlockPos(x + dx, height, z + dz);
                loaded[index] = level.isLoaded(probe);
                neighbours[index] = loaded[index]
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz)
                        : height;
                index++;
            }
        }
        return More4Rules.flat(height, neighbours, loaded, More4Rules.SPACE_LEVEL);
    }

    private static More4Rules.Pets pets(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int tamed = 0;
        int untamed = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(PET_RADIUS))) {
            if (animal.isRemoved()) {
                continue;
            }
            boolean wolf = animal instanceof Wolf;
            boolean cat = animal instanceof Cat;
            if (!wolf && !cat) {
                continue;
            }
            boolean isTame = (wolf && ((Wolf) animal).isTame()) || (cat && ((Cat) animal).isTame());
            if (isTame) {
                tamed++;
            } else {
                untamed++;
            }
        }
        return new More4Rules.Pets(tamed, untamed);
    }

    private static More4Rules.Rare rare(GraphFacts facts, SupplyGraph graph) {
        Map<String, Integer> carried = new LinkedHashMap<>();
        facts.spirits()
                .values()
                .forEach(spirit ->
                        spirit.inventory().forEach((item, count) -> carried.merge(item, count, Integer::sum)));
        return new More4Rules.Rare(More4Rules.rare(graph, carried), More4Rules.rareMixed(graph, facts.chests()));
    }

    private static More4Rules.Settlement settlement(GraphFacts facts) {
        int injured = 0;
        int hungry = 0;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            if (spirit.health() < More4Rules.INJURED_HEALTH) {
                injured++;
            }
            if (spirit.food() <= More4Rules.HUNGRY_FOOD) {
                hungry++;
            }
        }
        return new More4Rules.Settlement(injured, hungry);
    }
}

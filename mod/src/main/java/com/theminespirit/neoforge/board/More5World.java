package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More5Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class More5World {
    private static final int PLAYER_COMBAT_RADIUS = 16;
    private static final int GROW_RADIUS = 16;
    private static final int GROW_BELOW = 2;
    private static final int GROW_ABOVE = 3;
    private static final int GROW_LIGHT = 9;
    private static final int MINE_RADIUS = 8;
    private static final int MINE_BELOW = 16;
    private static final int DARK_LIGHT = 8;
    private static final int ROUTE_MAX_DISTANCE = 64;
    private static final int HOME_RADIUS = 32;
    private static final String SAPLINGS = "#saplings";
    private static final String BONE_MEAL = "bone_meal";
    private static final String MINE = "mine";
    private static final String FURNACE = "furnace";

    private More5World() {}

    static GraphState apply(
            GraphState state,
            ServerLevel level,
            SupplyGraph graph,
            FirstStepsRunner.StageScan scan,
            GraphSurroundings.Around around) {
        More5Rules.Signals signals = signals(state, level, graph, scan, around);
        return More5Inputs.events(state, More5Rules.events(signals));
    }

    private static More5Rules.Signals signals(
            GraphState state,
            ServerLevel level,
            SupplyGraph graph,
            FirstStepsRunner.StageScan scan,
            GraphSurroundings.Around around) {
        GraphFacts facts = state.facts();
        boolean boneMeal = state.count(BONE_MEAL) > 0;
        Growth growth = growth(level, scan.home(), boneMeal);
        int furnaces = state.stations(FURNACE);
        boolean furnace = furnaces > 0 || state.built(FURNACE) == GraphState.Built.BUILT;
        int fuel = More5Rules.fuel(graph, state.stock());
        int fuelTarget = More5Rules.fuelTarget(Math.max(1, furnaces));
        int shortage = More5Rules.buildShortage(state.materials(), state.stock(), built(state));
        int broken = More5Store.get(level).recentBreaks(level.getGameTime()).size() + carriedBlocks(level, scan.home());
        int cut = cutRoutes(level, facts, scan.home());
        int far = More5Rules.farSites(
                workSites(state, facts),
                facts.chests().stream().map(GraphFacts.Chest::position).toList(),
                More5Rules.CHEST_WORK_RADIUS);
        int darkMine = More5Inputs.darkMineCells(darkAroundMine(level, facts));
        int wood = More5Rules.woodDrops(around.drops().keySet(), saplings(graph));
        int weak = weakPlayers(level, facts);
        return new More5Rules.Signals(
                weak,
                growth.darkCrops(),
                growth.darkSaplings(),
                growth.dryCrops(),
                growth.unfed(),
                fuel,
                fuelTarget,
                furnace,
                shortage,
                broken,
                cut,
                far,
                darkMine,
                wood);
    }

    private static Set<String> built(GraphState state) {
        return state.buildings().entrySet().stream()
                .filter(entry -> entry.getValue() == GraphState.Built.BUILT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static int weakPlayers(ServerLevel level, GraphFacts facts) {
        if (facts.threats().isEmpty()) {
            return 0;
        }
        int weak = 0;
        for (ServerPlayer player : level.players()) {
            if (!inCombat(facts.threats(), player)) {
                continue;
            }
            float max = player.getMaxHealth();
            float ratio = max > 0 ? player.getHealth() / max : 1.0F;
            if (More5Rules.weakPlayer(ratio, player.getFoodData().getFoodLevel(), true)) {
                weak++;
            }
        }
        return weak;
    }

    private static boolean inCombat(List<GraphFacts.Threat> threats, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        long limit = (long) PLAYER_COMBAT_RADIUS * PLAYER_COMBAT_RADIUS;
        for (GraphFacts.Threat threat : threats) {
            if (threat.targetKind() == GraphFacts.TargetKind.PLAYER) {
                return true;
            }
            long dx = (long) threat.position().x() - at.getX();
            long dy = (long) threat.position().y() - at.getY();
            long dz = (long) threat.position().z() - at.getZ();
            if (dx * dx + dy * dy + dz * dz <= limit) {
                return true;
            }
        }
        return false;
    }

    private static Growth growth(ServerLevel level, BlockCoord home, boolean boneMeal) {
        int darkCrops = 0;
        int darkSaplings = 0;
        int dryCrops = 0;
        int unfed = 0;
        for (int dx = -GROW_RADIUS; dx <= GROW_RADIUS; dx++) {
            for (int dz = -GROW_RADIUS; dz <= GROW_RADIUS; dz++) {
                for (int dy = -GROW_BELOW; dy <= GROW_ABOVE; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.getBlock() instanceof CropBlock crop) {
                        if (!crop.isMaxAge(state)) {
                            if (level.getMaxLocalRawBrightness(at) < GROW_LIGHT) {
                                darkCrops++;
                            }
                            if (boneMeal) {
                                unfed++;
                            }
                        }
                    } else if (state.getBlock() instanceof SaplingBlock) {
                        if (level.getMaxLocalRawBrightness(at) < GROW_LIGHT) {
                            darkSaplings++;
                        }
                        if (boneMeal) {
                            unfed++;
                        }
                    } else if (state.getBlock() instanceof FarmBlock
                            && state.getValue(FarmBlock.MOISTURE) == 0
                            && level.getBlockState(at.above()).getBlock() instanceof CropBlock) {
                        dryCrops++;
                    }
                }
            }
        }
        return new Growth(darkCrops, darkSaplings, dryCrops, unfed);
    }

    private static int carriedBlocks(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int carried = 0;
        for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class, new AABB(center).inflate(HOME_RADIUS))) {
            if (enderman.isRemoved()) {
                continue;
            }
            BlockState block = enderman.getCarriedBlock();
            if (block != null && !block.isAir()) {
                carried++;
            }
        }
        return carried;
    }

    private static int cutRoutes(ServerLevel level, GraphFacts facts, BlockCoord home) {
        int cut = 0;
        for (GraphFacts.Resource resource : facts.resources()) {
            long dx = (long) resource.position().x() - home.x();
            long dy = (long) resource.position().y() - home.y();
            long dz = (long) resource.position().z() - home.z();
            if (dx * dx + dy * dy + dz * dz > (long) ROUTE_MAX_DISTANCE * ROUTE_MAX_DISTANCE) {
                continue;
            }
            if (More5Rules.cutByFluid(fluidLine(level, home, resource.position()), More5Rules.ROUTE_FLUID_WIDTH)) {
                cut++;
            }
        }
        return cut;
    }

    private static List<Boolean> fluidLine(ServerLevel level, BlockCoord from, GraphFacts.Position to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps < 3) {
            return List.of();
        }
        List<Boolean> line = new ArrayList<>(steps - 1);
        for (int step = 1; step < steps; step++) {
            int x = from.x() + (int) Math.round((double) dx * step / steps);
            int y = from.y() + (int) Math.round((double) dy * step / steps);
            int z = from.z() + (int) Math.round((double) dz * step / steps);
            BlockPos at = new BlockPos(x, y, z);
            if (!level.isLoaded(at)) {
                line.add(false);
                continue;
            }
            boolean fluid = level.getFluidState(at).is(FluidTags.WATER)
                    || level.getFluidState(at).is(FluidTags.LAVA)
                    || level.getFluidState(at.above()).is(FluidTags.WATER)
                    || level.getFluidState(at.above()).is(FluidTags.LAVA);
            line.add(fluid);
        }
        return line;
    }

    private static List<GraphFacts.Position> workSites(GraphState state, GraphFacts facts) {
        Set<GraphFacts.Position> sites = new LinkedHashSet<>();
        GraphFacts.Building mine = facts.buildings().get(MINE);
        if (mine != null && mine.position() != null) {
            sites.add(mine.position());
        }
        for (String id : state.materials().keySet()) {
            GraphFacts.Building building = facts.buildings().get(id);
            if (building != null && building.position() != null) {
                sites.add(building.position());
            }
        }
        return List.copyOf(sites);
    }

    private static int darkAroundMine(ServerLevel level, GraphFacts facts) {
        GraphFacts.Building mine = facts.buildings().get(MINE);
        if (mine == null || mine.position() == null) {
            return 0;
        }
        BlockPos center = new BlockPos(
                mine.position().x(), mine.position().y(), mine.position().z());
        int dark = 0;
        for (int dx = -MINE_RADIUS; dx <= MINE_RADIUS; dx++) {
            for (int dz = -MINE_RADIUS; dz <= MINE_RADIUS; dz++) {
                for (int dy = -MINE_BELOW; dy <= 0; dy++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) {
                        continue;
                    }
                    if (!level.getBlockState(at.below()).isAir() && level.getMaxLocalRawBrightness(at) < DARK_LIGHT) {
                        dark++;
                    }
                }
            }
        }
        return dark;
    }

    private static Set<String> saplings(SupplyGraph graph) {
        return graph.isGroup(SAPLINGS) ? Set.copyOf(graph.members(SAPLINGS)) : Set.of();
    }

    private record Growth(int darkCrops, int darkSaplings, int dryCrops, int unfed) {}
}

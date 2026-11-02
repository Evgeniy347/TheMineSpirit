package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.FarmRules;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

final class FarmInputs {
    private static final int RADIUS = 16;
    private static final int BELOW = 2;
    private static final int ABOVE = 2;

    private FarmInputs() {}

    static GraphState apply(GraphState state, ServerLevel level, SupplyGraph graph, FirstStepsRunner.StageScan scan) {
        Map<String, Integer> species = animals(level, scan.home(), false);
        Map<String, Integer> adults = animals(level, scan.home(), true);
        Map<String, Integer> herds = FarmRules.herdCounts(species);
        Map<String, Integer> stock = new LinkedHashMap<>(state.stock());
        herds.forEach((herd, count) -> stock.merge(herd, count, Math::max));
        FarmRules.Crops crops = crops(level, scan.home());
        int seeds = sum(graph, stock, "#seeds");
        int saplings = sum(graph, stock, "#saplings");
        boolean orchardEmpty = scan.stage().orchardSaplings().stream()
                .anyMatch(position -> level.isLoaded(pos(position))
                        && level.getBlockState(pos(position)).isAir());
        boolean hasFeed = sum(graph, stock, "#crops") > 0;
        Set<String> events = FarmRules.events(
                herds,
                FarmRules.herdCounts(adults),
                graph,
                state.spirits(),
                crops,
                seeds,
                new FarmRules.Signals(orchardEmpty, saplings > 0, hasFeed));
        return new GraphState(
                stock,
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                events,
                state.world(),
                state.distances(),
                state.stations(),
                state.orders(),
                state.facts());
    }

    private static Map<String, Integer> animals(ServerLevel level, BlockCoord home, boolean adultOnly) {
        Map<String, Integer> species = new LinkedHashMap<>();
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box(home))) {
            String kind = species(animal);
            if (kind != null && !animal.isRemoved() && (!adultOnly || !animal.isBaby())) {
                species.merge(kind, 1, Integer::sum);
            }
        }
        return species;
    }

    private static FarmRules.Crops crops(ServerLevel level, BlockCoord home) {
        int ready = 0;
        int dry = 0;
        int empty = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    BlockPos at = new BlockPos(home.x() + dx, home.y() + dy, home.z() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.getBlock() instanceof CropBlock crop) {
                        if (crop.isMaxAge(state)) {
                            ready++;
                        }
                    } else if (state.is(Blocks.FARMLAND)) {
                        if (state.getValue(FarmBlock.MOISTURE) == 0) {
                            dry++;
                        }
                        if (level.getBlockState(at.above()).isAir()) {
                            empty++;
                        }
                    }
                }
            }
        }
        return new FarmRules.Crops(ready, dry, empty);
    }

    private static int sum(SupplyGraph graph, Map<String, Integer> stock, String node) {
        if (!graph.isGroup(node)) {
            return stock.getOrDefault(node, 0);
        }
        return graph.members(node).stream()
                .mapToInt(member -> stock.getOrDefault(member, 0))
                .sum();
    }

    private static AABB box(BlockCoord home) {
        return new AABB(new BlockPos(home.x(), home.y(), home.z())).inflate(RADIUS);
    }

    private static BlockPos pos(BlockCoord position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private static @Nullable String species(Animal animal) {
        if (animal instanceof Chicken) {
            return "chicken";
        }
        if (animal instanceof Sheep) {
            return "sheep";
        }
        if (animal instanceof Pig) {
            return "pig";
        }
        if (animal instanceof Cow) {
            return "cow";
        }
        return null;
    }
}

package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.FarmRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.HerdCap;
import com.theminespirit.core.board.graph.More7Rules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.terminal.CraftOrderStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

final class More7Inputs {
    private static final int FIELD_RADIUS = 24;
    private static final int FIELD_BELOW = 4;
    private static final int FIELD_ABOVE = 4;
    private static final int ANIMAL_RADIUS = 48;
    private static final int PEN_RADIUS = 12;
    private static final String PEN_PREFIX = "pen_";

    private More7Inputs() {}

    static GraphState apply(GraphState state, ServerLevel level, SupplyGraph graph, FirstStepsRunner.StageScan scan) {
        Map<String, Integer> orders = More7Rules.liveOrders(graph, state.stage(), state.orders());
        int gated = state.orders().size() - orders.size();
        Field field = field(level, scan.home());
        More7Rules.DayPhase phase = More7Rules.phase(level.getDayTime());
        More7Rules.Signals signals = new More7Rules.Signals(
                More7Rules.workload(state.facts().spirits().values(), pendingNeeds(state)),
                gated,
                new More7Rules.Day(
                        phase == More7Rules.DayPhase.MORNING,
                        phase == More7Rules.DayPhase.EVENING,
                        field.ready(),
                        outside(state.facts(), scan)),
                pen(level, scan.home(), graph, state),
                new More7Rules.Capacity(field.planted(), field.farmland()),
                More7Rules.readyOrders(CraftOrderStore.get(level).book().all()));
        return More7Rules.merge(state, More7Rules.events(signals), orders);
    }

    private static int pendingNeeds(GraphState state) {
        return state.events().size() + state.orders().size();
    }

    private static int outside(GraphFacts facts, FirstStepsRunner.StageScan scan) {
        int outside = 0;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            if (!scan.stage()
                    .enclosure()
                    .contains(spirit.position().x(), spirit.position().z())) {
                outside++;
            }
        }
        return outside;
    }

    private static Field field(ServerLevel level, BlockCoord home) {
        int farmland = 0;
        int planted = 0;
        int ready = 0;
        for (int dx = -FIELD_RADIUS; dx <= FIELD_RADIUS; dx++) {
            for (int dz = -FIELD_RADIUS; dz <= FIELD_RADIUS; dz++) {
                for (int dy = -FIELD_BELOW; dy <= FIELD_ABOVE; dy++) {
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
                        farmland++;
                        if (level.getBlockState(at.above()).getBlock() instanceof CropBlock) {
                            planted++;
                        }
                    }
                }
            }
        }
        return new Field(farmland, planted, ready);
    }

    private static More7Rules.Capacity pen(ServerLevel level, BlockCoord home, SupplyGraph graph, GraphState state) {
        List<GraphFacts.Position> pens = state.facts().buildings().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PEN_PREFIX))
                .filter(entry -> state.built(entry.getKey()) == Built.BUILT)
                .map(entry -> entry.getValue().position())
                .filter(Objects::nonNull)
                .toList();
        Map<String, Integer> species = new LinkedHashMap<>();
        for (Animal animal : level.getEntitiesOfClass(
                Animal.class, new AABB(new BlockPos(home.x(), home.y(), home.z())).inflate(ANIMAL_RADIUS))) {
            String kind = species(animal);
            if (kind == null || animal.isRemoved() || !insidePen(animal.blockPosition(), pens)) {
                continue;
            }
            species.merge(kind, 1, Integer::sum);
        }
        int used = 0;
        int limit = 0;
        for (Map.Entry<String, Integer> herd : FarmRules.herdCounts(species).entrySet()) {
            HerdCap cap = graph.herdCaps().get(herd.getKey());
            if (cap == null) {
                continue;
            }
            used += herd.getValue();
            limit += cap.limit(state.spirits());
        }
        return new More7Rules.Capacity(used, limit);
    }

    private static boolean insidePen(BlockPos at, List<GraphFacts.Position> pens) {
        return pens.stream().anyMatch(pen -> {
            BlockPos center = new BlockPos(pen.x(), pen.y(), pen.z());
            return at.distSqr(center) <= (double) PEN_RADIUS * PEN_RADIUS;
        });
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

    private record Field(int farmland, int planted, int ready) {}
}

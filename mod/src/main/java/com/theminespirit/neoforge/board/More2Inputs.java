package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.More2Rules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

final class More2Inputs {
    private static final int ANIMAL_RADIUS = 48;
    private static final int PEN_RADIUS = 12;
    private static final int WILD_RADIUS = 16;
    private static final int VILLAGER_RADIUS = 48;
    private static final int BED_RADIUS = 20;
    private static final int BED_VERTICAL = 6;
    private static final int HAZARD_RADIUS = 3;
    private static final int FLOW_RADIUS = 8;
    private static final int SLOTS = 36;
    private static final String MINE = "mine";
    private static final String BED = "bed";
    private static final String CROP_READY = "crop_ready";
    private static final String STORE_MINIMUM = "store_minimum";
    private static final String TRANSFER_PENDING = "transfer_pending";
    private static final String PEN_PREFIX = "pen_";
    private static final String PATH_PREFIX = "path_";
    private static final String TRAIL = "more2_trail";

    private More2Inputs() {}

    static GraphState apply(GraphState state, ServerLevel level, FirstStepsRunner.StageScan scan) {
        More2Rules.Signals signals = signals(state, level, scan);
        Set<String> events = new LinkedHashSet<>(state.events());
        events.addAll(More2Rules.events(signals));
        return new GraphState(
                state.stock(),
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

    static More2Rules.Signals signals(GraphState state, ServerLevel level, FirstStepsRunner.StageScan scan) {
        Objects.requireNonNull(state, "Состояние не может быть null: вход области читается из него");
        Objects.requireNonNull(level, "Уровень не может быть null: огонь, животные и кровати есть только в мире");
        GraphFacts facts = state.facts();
        return new More2Rules.Signals(
                food(state),
                inventory(state, facts),
                carry(facts),
                hazards(level, facts, scan),
                animals(level, state, scan.home()),
                villagers(level, scan.home()),
                stage(state, facts),
                sleep(level, state, scan.home()),
                paths(state, scan));
    }

    private static More2Rules.Food food(GraphState state) {
        int animals = state.stock().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("herd_"))
                .mapToInt(entry -> entry.getValue())
                .sum();
        int need = state.facts().spirits().size() + animals / 4;
        int produce = (state.events().contains(CROP_READY) ? 4 : 0) + animals / 8;
        return new More2Rules.Food(state.count(GraphNames.FOOD), need, produce);
    }

    private static More2Rules.Inventory inventory(GraphState state, GraphFacts facts) {
        int free = SLOTS;
        boolean carries = false;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            carries |= !spirit.inventory().isEmpty();
            free = Math.min(free, Math.max(0, SLOTS - spirit.inventory().size()));
        }
        int freeChestSlots =
                facts.chests().stream().mapToInt(GraphFacts.Chest::freeSlots).sum();
        boolean work = state.events().contains(STORE_MINIMUM)
                || state.events().contains(TRANSFER_PENDING)
                || !state.orders().isEmpty();
        return new More2Rules.Inventory(free, freeChestSlots, carries, work);
    }

    private static More2Rules.Carry carry(GraphFacts facts) {
        int furthest = 0;
        for (GraphFacts.Spirit spirit : facts.spirits().values()) {
            for (GraphFacts.Chest chest : facts.chests()) {
                furthest = Math.max(furthest, distance(spirit.position(), chest.position()));
            }
        }
        return new More2Rules.Carry(furthest);
    }

    private static More2Rules.Hazards hazards(ServerLevel level, GraphFacts facts, FirstStepsRunner.StageScan scan) {
        int fire = 0;
        int lava = 0;
        for (GraphFacts.Building building : facts.buildings().values()) {
            if (building.position() == null) {
                continue;
            }
            int[] found = nearBuilding(level, building.position());
            fire += found[0];
            lava += found[1];
        }
        Flow flow = flow(
                level,
                List.of(new GraphFacts.Position(
                        scan.home().x(), scan.home().y(), scan.home().z())));
        GraphFacts.Building mine = facts.buildings().get(MINE);
        if (mine != null && mine.position() != null) {
            Flow atMine = flow(level, List.of(mine.position()));
            flow = new Flow(flow.water() + atMine.water(), flow.lava() + atMine.lava());
        }
        return new More2Rules.Hazards(fire, lava, flow.water(), flow.lava());
    }

    private static int[] nearBuilding(ServerLevel level, GraphFacts.Position position) {
        int[] counts = new int[2];
        BlockPos center = new BlockPos(position.x(), position.y(), position.z());
        for (int dx = -HAZARD_RADIUS; dx <= HAZARD_RADIUS; dx++) {
            for (int dy = -HAZARD_RADIUS; dy <= HAZARD_RADIUS; dy++) {
                for (int dz = -HAZARD_RADIUS; dz <= HAZARD_RADIUS; dz++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
                        counts[0]++;
                    } else if (state.is(Blocks.LAVA)) {
                        counts[1]++;
                    }
                }
            }
        }
        return counts;
    }

    private static Flow flow(ServerLevel level, List<GraphFacts.Position> centers) {
        int water = 0;
        int lava = 0;
        for (GraphFacts.Position center : centers) {
            BlockPos at = new BlockPos(center.x(), center.y(), center.z());
            for (int dx = -FLOW_RADIUS; dx <= FLOW_RADIUS; dx++) {
                for (int dy = -FLOW_RADIUS; dy <= FLOW_RADIUS; dy++) {
                    for (int dz = -FLOW_RADIUS; dz <= FLOW_RADIUS; dz++) {
                        BlockPos pos = at.offset(dx, dy, dz);
                        if (!level.isLoaded(pos)) {
                            continue;
                        }
                        var fluid = level.getFluidState(pos);
                        if (fluid.isEmpty() || fluid.isSource()) {
                            continue;
                        }
                        if (fluid.is(FluidTags.WATER)) {
                            water++;
                        } else if (fluid.is(FluidTags.LAVA)) {
                            lava++;
                        }
                    }
                }
            }
        }
        return new Flow(water, lava);
    }

    private static More2Rules.Animals animals(ServerLevel level, GraphState state, BlockCoord home) {
        List<GraphFacts.Position> pens = state.facts().buildings().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(PEN_PREFIX))
                .filter(entry -> state.built(entry.getKey()) == GraphState.Built.BUILT)
                .map(entry -> entry.getValue().position())
                .filter(Objects::nonNull)
                .toList();
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int total = 0;
        int penned = 0;
        int wild = 0;
        for (Animal animal : level.getEntitiesOfClass(Animal.class, box(center, ANIMAL_RADIUS))) {
            if (animal.isRemoved()) {
                continue;
            }
            total++;
            BlockPos at = animal.blockPosition();
            if (insidePen(at, pens)) {
                penned++;
            } else if (at.distSqr(center) > (double) WILD_RADIUS * WILD_RADIUS) {
                wild++;
            }
        }
        return new More2Rules.Animals(total, penned, wild, !pens.isEmpty());
    }

    private static More2Rules.Villagers villagers(ServerLevel level, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        return new More2Rules.Villagers(level.getEntitiesOfClass(Villager.class, box(center, VILLAGER_RADIUS))
                .size());
    }

    private static More2Rules.Stage stage(GraphState state, GraphFacts facts) {
        int stages = DataTables.INSTANCE.stages().current().stages().size();
        int idle = (int) facts.spirits().values().stream()
                .filter(spirit -> spirit.activity() == null || spirit.activity().isBlank())
                .count();
        return new More2Rules.Stage(state.stage(), stages, idle);
    }

    private static More2Rules.Sleep sleep(ServerLevel level, GraphState state, BlockCoord home) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        int placed = 0;
        for (int dx = -BED_RADIUS; dx <= BED_RADIUS; dx++) {
            for (int dy = -BED_VERTICAL; dy <= BED_VERTICAL; dy++) {
                for (int dz = -BED_RADIUS; dz <= BED_RADIUS; dz++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof BedBlock) {
                        placed++;
                    }
                }
            }
        }
        return new More2Rules.Sleep(
                placed + state.count(BED), state.facts().spirits().size());
    }

    private static More2Rules.Paths paths(GraphState state, FirstStepsRunner.StageScan scan) {
        int outside = 0;
        for (GraphFacts.Spirit spirit : state.facts().spirits().values()) {
            if (!scan.stage()
                    .enclosure()
                    .contains(spirit.position().x(), spirit.position().z())) {
                outside++;
            }
        }
        int trails = state.buildings().entrySet().stream()
                        .filter(entry -> entry.getValue() == GraphState.Built.BUILT)
                        .filter(entry -> entry.getKey().startsWith(PATH_PREFIX) || TRAIL.equals(entry.getKey()))
                        .findAny()
                        .isPresent()
                ? 1
                : 0;
        return new More2Rules.Paths(outside, trails);
    }

    private static boolean insidePen(BlockPos at, List<GraphFacts.Position> pens) {
        return pens.stream().anyMatch(pen -> {
            BlockPos center = new BlockPos(pen.x(), pen.y(), pen.z());
            return at.distSqr(center) <= (double) PEN_RADIUS * PEN_RADIUS;
        });
    }

    private static AABB box(BlockPos center, int radius) {
        return new AABB(center).inflate(radius);
    }

    private static int distance(GraphFacts.Position first, GraphFacts.Position second) {
        int dx = first.x() - second.x();
        int dy = first.y() - second.y();
        int dz = first.z() - second.z();
        return (int) Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
    }

    private record Flow(int water, int lava) {}
}

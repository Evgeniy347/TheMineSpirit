package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.StoreInputs;
import com.theminespirit.core.board.graph.StoreMinimums;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.inventory.ContainerSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jspecify.annotations.Nullable;

final class GraphStoreInputs {
    private static final String FURNACE = "furnace";
    private static final int FURNACE_RADIUS = 4;
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_OUTPUT = 2;

    private GraphStoreInputs() {}

    static GraphFacts of(GraphFacts base, List<ContainerSnapshot> chests, ServerLevel level, SupplyGraph graph) {
        List<GraphFacts.Chest> known = StoreInputs.chestFacts(chests);
        GraphFacts.StageReqs reqs = StoreInputs.stageReqs(base.stageReqs(), StoreMinimums.loadDefault(), graph, known);
        Map<String, GraphFacts.Station> stations = new LinkedHashMap<>(base.stations());
        stations.replaceAll((id, station) -> readFurnace(level, station));
        return new GraphFacts(
                base.spirits(),
                base.threats(),
                known,
                base.resources(),
                stations,
                base.buildings(),
                base.strays(),
                base.terrain(),
                reqs);
    }

    private static GraphFacts.Station readFurnace(ServerLevel level, GraphFacts.Station station) {
        if (!FURNACE.equals(station.type()) || station.position() == null) {
            return station;
        }
        Optional<AbstractFurnaceBlockEntity> found = findFurnace(level, station.position());
        if (found.isEmpty()) {
            return station;
        }
        AbstractFurnaceBlockEntity furnace = found.orElseThrow();
        StoreInputs.FurnaceSlots slots = new StoreInputs.FurnaceSlots(
                item(furnace.getItem(SLOT_INPUT)),
                item(furnace.getItem(SLOT_FUEL)),
                item(furnace.getItem(SLOT_OUTPUT)));
        boolean lit = furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT);
        return StoreInputs.furnaceStation(station, slots, lit);
    }

    private static Optional<AbstractFurnaceBlockEntity> findFurnace(ServerLevel level, GraphFacts.Position position) {
        BlockPos center = new BlockPos(position.x(), position.y(), position.z());
        AbstractFurnaceBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (int dx = -FURNACE_RADIUS; dx <= FURNACE_RADIUS; dx++) {
            for (int dy = -FURNACE_RADIUS; dy <= FURNACE_RADIUS; dy++) {
                for (int dz = -FURNACE_RADIUS; dz <= FURNACE_RADIUS; dz++) {
                    BlockPos at = center.offset(dx, dy, dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    if (level.getBlockEntity(at) instanceof AbstractFurnaceBlockEntity furnace) {
                        double distance = at.distSqr(center);
                        if (distance < best) {
                            best = distance;
                            nearest = furnace;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static @Nullable String item(ItemStack stack) {
        return stack.isEmpty()
                ? null
                : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}

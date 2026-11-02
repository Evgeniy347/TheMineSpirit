package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.StoreFacts;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

final class StoreInputs {
    private static final int SLOT_INPUT = 0;
    private static final int SLOT_FUEL = 1;
    private static final int SLOT_RESULT = 2;

    private StoreInputs() {}

    static GraphFacts apply(ServerLevel level, int stage, SupplyGraph graph, GraphFacts facts) {
        GraphFacts.StageReqs reqs =
                StoreFacts.stageReqs(graph, StoreFacts.loadDefault(), stage, facts.chests(), facts.spirits());
        return facts.withStageReqs(reqs).withStations(stations(level, facts.stations()));
    }

    private static Map<String, GraphFacts.Station> stations(ServerLevel level, Map<String, GraphFacts.Station> known) {
        Map<String, GraphFacts.Station> stations = new LinkedHashMap<>();
        known.forEach((id, station) -> stations.put(id, furnace(level, station)));
        return stations;
    }

    private static GraphFacts.Station furnace(ServerLevel level, GraphFacts.Station station) {
        if (!"furnace".equals(station.type()) && !"smoker".equals(station.type())) {
            return station;
        }
        GraphFacts.Position at = station.position();
        if (at == null) {
            return station;
        }
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        if (!level.isLoaded(pos)) {
            return station;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof AbstractFurnaceBlockEntity furnace)) {
            return station;
        }
        boolean busy = level.getBlockState(pos).getValue(AbstractFurnaceBlock.LIT);
        return new GraphFacts.Station(
                station.id(),
                station.type(),
                at,
                busy,
                item(furnace.getItem(SLOT_FUEL)),
                item(furnace.getItem(SLOT_INPUT)),
                item(furnace.getItem(SLOT_RESULT)));
    }

    private static @Nullable String item(ItemStack stack) {
        return stack.isEmpty()
                ? null
                : GraphNames.graphItem(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}

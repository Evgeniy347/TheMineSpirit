package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphFactsUpdates;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.StoreRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jspecify.annotations.Nullable;

final class StoreInputs {
    private StoreInputs() {}

    static GraphState apply(GraphState state, ServerLevel level, SupplyGraph graph) {
        StoreRules.Config config = StoreRules.config();
        GraphFacts facts = state.facts();
        Map<String, Integer> actual = StoreRules.actual(facts.chests(), facts.spirits());
        Map<String, Integer> grouped =
                StoreRules.groupCounts(graph, actual, config.minimums().keySet());
        Map<String, Integer> shortfall = StoreRules.shortfalls(config.minimums(), grouped, config.reservePercent());
        Map<String, Integer> stock = new LinkedHashMap<>(state.stock());
        actual.forEach((item, count) -> stock.merge(item, count, Math::max));

        Map<String, Integer> minimum = new LinkedHashMap<>(facts.stageReqs().minimum());
        Map<String, Integer> real = new LinkedHashMap<>(facts.stageReqs().actual());
        config.minimums().forEach((node, amount) -> {
            minimum.putIfAbsent(node, StoreRules.withReserve(amount, config.reservePercent()));
            real.putIfAbsent(node, grouped.getOrDefault(node, 0));
        });
        config.toolMinimums().forEach((kind, tier) -> {
            String node = tier + "_" + kind;
            int have = state.bestTier(kind) >= graph.rank(tier) ? 1 : 0;
            minimum.putIfAbsent(node, 1);
            real.putIfAbsent(node, have);
            if (have == 0) {
                shortfall.put(node, 1);
            }
        });

        Map<String, GraphFacts.Station> stations = stationContents(level, facts.stations());
        GraphFacts known = GraphFactsUpdates.withStageReqs(
                GraphFactsUpdates.withStations(facts, stations), new GraphSiteFacts.StageReqs(minimum, real));
        boolean storageFull = state.storageFreePercent() < graph.storage().minFreePercent();
        int mixed = StoreRules.mixedChests(facts.chests(), config.maxItemTypesPerChest());
        boolean carried = facts.spirits().values().stream()
                .anyMatch(spirit -> !spirit.inventory().isEmpty());
        int freeSlots =
                facts.chests().stream().mapToInt(GraphFacts.Chest::freeSlots).sum();
        boolean furnaceReady = stations.values().stream().anyMatch(station -> station.output() != null);
        boolean furnaceNeedsFuel = stations.values().stream()
                .anyMatch(station ->
                        "furnace".equals(station.type()) && station.input() != null && station.fuel() == null);
        Set<String> events = StoreRules.events(
                state.events(),
                new StoreRules.Signals(
                        !shortfall.isEmpty(),
                        storageFull,
                        mixed > 0,
                        carried && freeSlots > 0,
                        furnaceReady,
                        furnaceNeedsFuel));
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
                known);
    }

    private static Map<String, GraphFacts.Station> stationContents(
            ServerLevel level, Map<String, GraphFacts.Station> known) {
        Map<String, GraphFacts.Station> stations = new LinkedHashMap<>();
        known.forEach((id, station) -> stations.put(id, content(level, station)));
        return stations;
    }

    private static GraphFacts.Station content(ServerLevel level, GraphFacts.Station station) {
        GraphFacts.Position position = station.position();
        if (position == null) {
            return station;
        }
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) {
            return station;
        }
        String input = item(furnace.getItem(0));
        String fuel = item(furnace.getItem(1));
        String output = item(furnace.getItem(2));
        boolean busy = furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT) || input != null;
        return new GraphFacts.Station(station.id(), station.type(), position, busy, fuel, input, output);
    }

    private static @Nullable String item(ItemStack stack) {
        return stack.isEmpty()
                ? null
                : GraphNames.graphItem(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
}

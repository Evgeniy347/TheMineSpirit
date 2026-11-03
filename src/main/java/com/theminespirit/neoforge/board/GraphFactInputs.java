package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphKits;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

final class GraphFactInputs {
    private GraphFactInputs() {}

    static GraphFacts of(
            BoardService service,
            List<SpiritEntity> spirits,
            SupplyGraph graph,
            Map<String, Built> buildings,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            GraphSurroundings.Around around) {
        return new GraphFacts(
                spiritFacts(service, spirits, graph),
                List.of(),
                List.of(),
                resourceFacts(around, graph),
                stationFacts(graph, buildings, town, scan),
                buildingFacts(buildings, town, scan),
                List.of(),
                GraphFacts.Terrain.empty(),
                stageReqs(service, service.board().ledger().stage()));
    }

    private static Map<String, GraphFacts.Spirit> spiritFacts(
            BoardService service, List<SpiritEntity> spirits, SupplyGraph graph) {
        long now = service.board().now();
        Map<String, GraphFacts.Spirit> facts = new LinkedHashMap<>();
        for (SpiritEntity spirit : spirits) {
            String id = spirit.getUUID().toString();
            facts.put(
                    id,
                    new GraphFacts.Spirit(
                            id,
                            position(spirit),
                            spirit.getHealth(),
                            spirit.hunger().foodLevel(),
                            spirit.getAirSupply(),
                            spirit.isInWater(),
                            false,
                            service.board().stuck().isStuck(spirit.getUUID(), now),
                            spirit.currentActivity().orElse(null),
                            inventory(spirit),
                            armor(spirit),
                            tools(spirit, graph),
                            null));
        }
        return facts;
    }

    private static GraphFacts.Position position(SpiritEntity spirit) {
        return new GraphFacts.Position(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
    }

    private static Map<String, Integer> inventory(SpiritEntity spirit) {
        Map<String, Integer> inventory = new LinkedHashMap<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            addItem(inventory, spirit.getInventory().getItem(slot));
        }
        if (spirit.mainHandOwned()) {
            addItem(inventory, spirit.getMainHandItem());
        }
        return inventory;
    }

    private static void addItem(Map<String, Integer> inventory, ItemStack stack) {
        if (!stack.isEmpty()) {
            inventory.merge(itemId(stack), stack.getCount(), Integer::sum);
        }
    }

    private static List<GraphFacts.Armor> armor(SpiritEntity spirit) {
        List<GraphFacts.Armor> armor = new ArrayList<>();
        for (EquipmentSlot slot :
                List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack stack = spirit.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                armor.add(new GraphFacts.Armor(slot.name(), itemId(stack), durability(stack), stack.getMaxDamage()));
            }
        }
        return armor;
    }

    private static List<GraphFacts.Tool> tools(SpiritEntity spirit, SupplyGraph graph) {
        List<GraphFacts.Tool> tools = new ArrayList<>();
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            addTool(tools, spirit.getInventory().getItem(slot), graph);
        }
        if (spirit.mainHandOwned()) {
            addTool(tools, spirit.getMainHandItem(), graph);
        }
        return tools;
    }

    private static void addTool(List<GraphFacts.Tool> tools, ItemStack stack, SupplyGraph graph) {
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) {
            return;
        }
        String item = itemId(stack);
        GraphKits.of(List.of(item), graph)
                .forEach((kind, tier) ->
                        tools.add(new GraphFacts.Tool(item, kind, tier, durability(stack), stack.getMaxDamage())));
    }

    private static int durability(ItemStack stack) {
        return stack.getMaxDamage() > 0 ? stack.getMaxDamage() - stack.getDamageValue() : 0;
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static List<GraphFacts.Chest> chestFacts(List<ContainerSnapshot> snapshots) {
        List<GraphFacts.Chest> chests = new ArrayList<>();
        for (ContainerSnapshot snapshot : snapshots) {
            Map<String, Integer> contents = new LinkedHashMap<>();
            for (Stack stack : snapshot.slots()) {
                if (stack != null) {
                    contents.merge(stack.item(), stack.count(), Integer::sum);
                }
            }
            BlockCoord pos = snapshot.pos();
            chests.add(new GraphFacts.Chest(
                    new GraphFacts.Position(pos.x(), pos.y(), pos.z()),
                    contents,
                    snapshot.freeSlots(),
                    snapshot.slotCount()));
        }
        return chests;
    }

    static List<GraphFacts.Resource> resourceFacts(GraphSurroundings.Around around, SupplyGraph graph) {
        List<GraphFacts.Resource> resources = new ArrayList<>();
        around.distances().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> around.resource(entry.getKey()).ifPresent(at -> {
                    int distance = entry.getValue();
                    resources.add(new GraphFacts.Resource(
                            entry.getKey(),
                            new GraphFacts.Position(at.x(), at.y(), at.z()),
                            distance,
                            GraphFacts.Reach.UNKNOWN,
                            graph.costs().travel(distance)));
                }));
        return resources;
    }

    private static Map<String, GraphFacts.Station> stationFacts(
            SupplyGraph graph, Map<String, Built> buildings, Optional<TownPlan> town, FirstStepsRunner.StageScan scan) {
        Map<String, GraphFacts.Station> stations = new LinkedHashMap<>();
        graph.costs().stationCapacity().keySet().stream().sorted().forEach(id -> {
            if (buildings.get(id) == Built.BUILT) {
                stations.put(id, new GraphFacts.Station(id, id, position(id, town, scan), false, null, null, null));
            }
        });
        return stations;
    }

    static Map<String, GraphFacts.Building> buildingFacts(
            Map<String, Built> buildings, Optional<TownPlan> town, FirstStepsRunner.StageScan scan) {
        Map<String, GraphFacts.Building> facts = new LinkedHashMap<>();
        buildings.forEach((id, state) -> {
            boolean built = state == Built.BUILT;
            facts.put(
                    id,
                    new GraphFacts.Building(
                            id,
                            built ? 1 : 0,
                            built ? 100 : 0,
                            0,
                            Map.of(),
                            Map.of(),
                            0,
                            position(id, town, scan),
                            false));
        });
        return facts;
    }

    private static GraphFacts.@Nullable Position position(
            String id, Optional<TownPlan> town, FirstStepsRunner.StageScan scan) {
        return town.flatMap(plan ->
                        StageCatalog.origin(id, plan, scan.stage().anchor().y()))
                .map(at -> new GraphFacts.Position(at.x(), at.y(), at.z()))
                .orElse(null);
    }

    static Map<String, Integer> stationCounts(SupplyGraph graph, Map<String, Built> buildings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        graph.costs().stationCapacity().keySet().stream()
                .sorted()
                .forEach(id -> counts.put(id, buildings.get(id) == Built.BUILT ? 1 : 0));
        return counts;
    }

    private static GraphFacts.StageReqs stageReqs(BoardService service, int stage) {
        StageRules rules = DataTables.INSTANCE.stages().current();
        if (stage > rules.stages().size()) {
            return new GraphFacts.StageReqs(Map.of(), Map.of());
        }
        Map<String, Integer> minimum = rules.stages().get(stage - 1).requires();
        Map<String, Integer> actual = new LinkedHashMap<>();
        minimum.forEach((id, level) -> actual.put(id, service.board().ledger().hasPlan(id, level) ? level : 0));
        return new GraphFacts.StageReqs(minimum, actual);
    }
}

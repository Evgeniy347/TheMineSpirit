package com.theminespirit.core.board.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

final class GraphSimulationEvents {
    private GraphSimulationEvents() {}

    static void after(GraphWorld world, String event) {
        if (event.startsWith("life_threat")) {
            clearThreat(world);
        } else if (event.startsWith("repair_")) {
            repair(world, event.substring("repair_".length()));
        } else if ("tool_worn".equals(event) || "armor_worn".equals(event)) {
            restoreWear(world);
        } else if ("gear_equip_best".equals(event)) {
            emptyChests(world);
        } else if (event.startsWith("gear_craft_")) {
            craftGear(world, event.substring("gear_craft_".length()));
        } else if (event.startsWith("gear_kit_")) {
            clearActivity(world);
        } else if (GraphWorldEvents.DEMOLISH_STRAY.equals(event)) {
            removeStray(world);
        } else if (GraphWorldEvents.BACKFILL.equals(event)
                || GraphWorldEvents.CLEAR_FLOATING.equals(event)
                || GraphWorldEvents.LEVEL_LOT.equals(event)
                || GraphWorldEvents.RESTORE_TRACE.equals(event)) {
            terrain(world, event);
        }
    }

    private static void clearThreat(GraphWorld world) {
        GraphFacts facts = world.facts;
        if (!facts.threats().isEmpty()) {
            world.facts =
                    facts.withThreats(facts.threats().subList(1, facts.threats().size()));
        }
    }

    private static void repair(GraphWorld world, String id) {
        GraphFacts facts = world.facts;
        GraphFacts.Building before = facts.buildings().get(id);
        if (before == null) {
            return;
        }
        Map<String, GraphFacts.Building> next = new LinkedHashMap<>(facts.buildings());
        next.put(
                id,
                new GraphFacts.Building(
                        id,
                        before.level(),
                        before.auditPercent(),
                        0,
                        Map.of(),
                        Map.of(),
                        0,
                        before.position(),
                        before.unknown(),
                        before.lotReady()));
        world.facts = withBuildings(facts, next);
    }

    private static void restoreWear(GraphWorld world) {
        world.facts = withSpirits(
                world.facts,
                spirit -> copy(
                        spirit,
                        spirit.activity(),
                        spirit.armor().stream()
                                .map(armor -> new GraphFacts.Armor(
                                        armor.slot(), armor.item(), armor.maxDurability(), armor.maxDurability()))
                                .toList(),
                        spirit.tools().stream()
                                .map(tool -> new GraphFacts.Tool(
                                        tool.item(),
                                        tool.kind(),
                                        tool.tier(),
                                        tool.maxDurability(),
                                        tool.maxDurability()))
                                .toList()));
    }

    private static void emptyChests(GraphWorld world) {
        world.facts = world.facts.withChests(world.facts.chests().stream()
                .map(chest -> new GraphFacts.Chest(
                        chest.position(), Map.of(), chest.totalSlots(), chest.totalSlots(), GraphFacts.Reach.UNKNOWN))
                .toList());
    }

    private static void craftGear(GraphWorld world, String kind) {
        world.facts = withSpirits(
                world.facts,
                spirit -> "armor".equals(kind)
                        ? copy(spirit, spirit.activity(), armor(), spirit.tools())
                        : copy(
                                spirit,
                                spirit.activity(),
                                spirit.armor(),
                                append(
                                        spirit.tools(),
                                        new GraphFacts.Tool("minecraft:netherite_" + kind, kind, 4, 100, 100))));
    }

    private static void clearActivity(GraphWorld world) {
        world.facts = withSpirits(world.facts, spirit -> copy(spirit, null, spirit.armor(), spirit.tools()));
    }

    private static void removeStray(GraphWorld world) {
        GraphFacts facts = world.facts;
        if (!facts.strays().isEmpty()) {
            world.facts =
                    withStrays(facts, facts.strays().subList(1, facts.strays().size()));
        }
    }

    private static void terrain(GraphWorld world, String event) {
        GraphFacts facts = world.facts;
        GraphSiteFacts.Terrain terrain = facts.terrain();
        int pits = terrain.pits();
        int hanging = terrain.hanging();
        int heightDrop = terrain.heightDrop();
        int traces = terrain.traces();
        Map<String, Integer> cut = terrain.cut();
        Map<String, Integer> fill = terrain.fill();
        Map<String, Integer> floating = terrain.floating();
        if (GraphWorldEvents.BACKFILL.equals(event)) {
            pits = Math.max(0, pits - 1);
            fill = Map.of();
        } else if (GraphWorldEvents.CLEAR_FLOATING.equals(event)) {
            hanging = Math.max(0, hanging - 1);
            floating = Map.of();
        } else if (GraphWorldEvents.LEVEL_LOT.equals(event)) {
            heightDrop = 0;
            cut = Map.of();
        } else {
            traces = Math.max(0, traces - 1);
        }
        world.facts = withTerrain(
                facts,
                new GraphSiteFacts.Terrain(
                        pits,
                        hanging,
                        heightDrop,
                        terrain.deepestPit(),
                        traces,
                        cut,
                        fill,
                        floating,
                        terrain.unknown(),
                        terrain.natural()));
    }

    private static GraphFacts.Spirit copy(
            GraphFacts.Spirit spirit, String activity, List<GraphFacts.Armor> armor, List<GraphFacts.Tool> tools) {
        return new GraphFacts.Spirit(
                spirit.id(),
                spirit.position(),
                spirit.health(),
                Math.max(spirit.health(), 1f),
                null,
                spirit.food(),
                spirit.air(),
                spirit.inWater(),
                spirit.trapped(),
                spirit.stuck(),
                activity,
                spirit.inventory(),
                armor,
                tools,
                spirit.trap());
    }

    private static List<GraphFacts.Armor> armor() {
        return List.of(
                new GraphFacts.Armor("HEAD", "minecraft:netherite_helmet", 100, 100),
                new GraphFacts.Armor("CHEST", "minecraft:netherite_chestplate", 100, 100),
                new GraphFacts.Armor("LEGS", "minecraft:netherite_leggings", 100, 100),
                new GraphFacts.Armor("FEET", "minecraft:netherite_boots", 100, 100));
    }

    private static List<GraphFacts.Tool> append(List<GraphFacts.Tool> tools, GraphFacts.Tool tool) {
        List<GraphFacts.Tool> next = new ArrayList<>(tools);
        next.add(tool);
        return List.copyOf(next);
    }

    private static GraphFacts withBuildings(GraphFacts facts, Map<String, GraphFacts.Building> buildings) {
        return new GraphFacts(
                facts.spirits(),
                facts.threats(),
                facts.chests(),
                facts.resources(),
                facts.stations(),
                buildings,
                facts.strays(),
                facts.terrain(),
                facts.stageReqs(),
                facts.feedback(),
                List.of(),
                List.of());
    }

    private static GraphFacts withStrays(GraphFacts facts, List<GraphSiteFacts.Stray> strays) {
        return new GraphFacts(
                facts.spirits(),
                facts.threats(),
                facts.chests(),
                facts.resources(),
                facts.stations(),
                facts.buildings(),
                strays,
                facts.terrain(),
                facts.stageReqs(),
                facts.feedback(),
                List.of(),
                List.of());
    }

    private static GraphFacts withTerrain(GraphFacts facts, GraphSiteFacts.Terrain terrain) {
        return new GraphFacts(
                facts.spirits(),
                facts.threats(),
                facts.chests(),
                facts.resources(),
                facts.stations(),
                facts.buildings(),
                facts.strays(),
                terrain,
                facts.stageReqs(),
                facts.feedback(),
                List.of(),
                List.of());
    }

    private static GraphFacts withSpirits(GraphFacts facts, UnaryOperator<GraphFacts.Spirit> change) {
        Map<String, GraphFacts.Spirit> next = new LinkedHashMap<>();
        facts.spirits().forEach((id, spirit) -> next.put(id, change.apply(spirit)));
        return facts.withSpirits(next);
    }
}

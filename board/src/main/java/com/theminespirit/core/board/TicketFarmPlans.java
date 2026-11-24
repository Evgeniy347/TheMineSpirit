package com.theminespirit.core.board;

import static com.theminespirit.core.board.TicketPlans.*;

import com.theminespirit.core.building.SchematicPart;
import com.theminespirit.core.husbandry.api.FeedTable;
import com.theminespirit.core.husbandry.api.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.Locale;
import java.util.Map;

final class TicketFarmPlans {
    private TicketFarmPlans() {}

    static TaskNode repairFence(TicketTarget target) {
        return FencePlans.repair(target);
    }

    static TaskNode captureAnimal(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return luredCapture(species, FeedTable.feedFor(species).get(0));
    }

    static TaskNode lureAnimal(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        String lure = target.lureItem() == null ? DEFAULT_LURE_ITEM : target.lureItem();
        return luredCapture(species, lure);
    }

    private static TaskNode luredCapture(Species species, String feed) {
        return sequence(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                feed,
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                new ScriptCall(
                        "capture",
                        Map.of(
                                SPECIES,
                                species.name().toLowerCase(Locale.ROOT),
                                RADIUS,
                                CatalogLimits.CAPTURE_RADIUS_DEFAULT)));
    }

    static TaskNode buildFence(TicketTarget target, int level) {
        int part = target.count();
        String scheme =
                part >= 1 && part <= fenceParts() ? SchematicPart.name(WALL_SCHEME, part, fenceParts()) : WALL_SCHEME;
        return schematic(scheme, target.target(), target);
    }

    static TaskNode placeInYard(String item, int count, TicketTarget target) {
        return Leftovers.with(
                sequence(
                        gotoTarget(target),
                        new ScriptCall(
                                CRAFT,
                                Map.of(
                                        ITEM,
                                        item,
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT,
                                        PLACE,
                                        true))),
                target);
    }

    static TaskNode tableNearSpirit(TicketTarget target) {
        return Leftovers.with(
                new ScriptCall(
                        CRAFT,
                        Map.of(
                                ITEM,
                                "minecraft:crafting_table",
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.STATION_RADIUS_DEFAULT,
                                PLACE,
                                true)),
                target);
    }

    static TaskNode yard(TicketTarget target) {
        return Leftovers.with(
                sequence(
                        gotoTarget(target),
                        new ScriptCall(
                                CRAFT, Map.of(ITEM, "minecraft:crafting_table", COUNT, 1, RADIUS, 1, PLACE, true))),
                target);
    }

    static TaskNode breedAnimals(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall(
                "breed",
                Map.of(
                        SPECIES,
                        species.name().toLowerCase(Locale.ROOT),
                        RADIUS,
                        CatalogLimits.HERD_RADIUS_DEFAULT,
                        "pairs",
                        CatalogLimits.HERD_PAIRS_DEFAULT));
    }

    static TaskNode herdCull(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall(
                "cull",
                Map.of(SPECIES, species.name().toLowerCase(Locale.ROOT), RADIUS, CatalogLimits.HERD_RADIUS_DEFAULT));
    }

    static TaskNode shearSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.minPenSheep();
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "shear",
                        Map.of(
                                RADIUS, WoolRules.shearRadius(),
                                COUNT, Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }

    static TaskNode harvestHive() {
        return new ScriptCall(
                "harvest_hive", Map.of(RADIUS, HiveRules.hiveRadius(), COUNT, CatalogLimits.HIVE_COUNT_DEFAULT));
    }

    static TaskNode placeBeehive(TicketTarget target) {
        BlockCoord point = target.target();
        BlockCoord cell = new BlockCoord(point.x(), point.y() + 1, point.z());
        return sequence(
                craft(HiveRules.CRAFTED_BEEHIVE, 1, target),
                new ScriptCall(
                        "place_block",
                        Map.of(BLOCK, HiveRules.CRAFTED_BEEHIVE, "x", cell.x(), "y", cell.y(), "z", cell.z())));
    }

    static TaskNode huntWildSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.wildSheepPerDay();
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "hunt",
                        Map.of(
                                SPECIES, Species.SHEEP.name().toLowerCase(Locale.ROOT),
                                RADIUS, WoolRules.wildSheepRadius(),
                                COUNT, Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }
}

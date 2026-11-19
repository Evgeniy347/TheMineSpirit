package com.theminespirit.core.board;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.plan.TaskNode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class DefaultPlans {
    private static final Map<BoardCase, PlanFactory> PLANS = plans();

    private DefaultPlans() {}

    static TaskNode plan(BoardCase boardCase, TicketTarget target, int level) {
        PlanFactory factory = PLANS.get(boardCase);
        if (factory == null) {
            throw new IllegalArgumentException("Кейс " + boardCase
                    + " не имеет плана на доске: выдача задания без плана оставила бы духа "
                    + "без задачи — новый кейс обязан прийти со своей строкой плана "
                    + "или записью в EXTERNAL_CASES");
        }
        return factory.build(boardCase, target, level);
    }

    private static Map<BoardCase, PlanFactory> plans() {
        Map<BoardCase, PlanFactory> plans = new EnumMap<>(BoardCase.class);
        plans.put(BoardCase.FILL_CRATER, (boardCase, target, level) -> PitFillPlan.plan(target));
        plans.put(BoardCase.REPAIR_FENCE, (boardCase, target, level) -> TicketFarmPlans.repairFence(target));
        plans.put(BoardCase.CAPTURE_ANIMAL, (boardCase, target, level) -> TicketFarmPlans.captureAnimal(target));
        plans.put(BoardCase.LURE_ANIMAL, (boardCase, target, level) -> TicketFarmPlans.lureAnimal(target));
        plans.put(BoardCase.SUPPLY_SITE, (boardCase, target, level) -> TicketStoragePlans.supplySite(target));
        plans.put(
                BoardCase.GATHER_MATERIALS,
                (boardCase, target, level) -> target.leveling() == null
                        ? TicketStoragePlans.gatherMaterials(target)
                        : RoutePlans.dig(target));
        plans.put(
                BoardCase.CLEAR_GROUNDS,
                (boardCase, target, level) -> Leftovers.with(GroundsPlan.plan(target), target));
        plans.put(BoardCase.COLLECT_DROPS, (boardCase, target, level) -> TicketPlans.collectDrops(target));
        plans.put(BoardCase.REPAIR_BUILDING, (boardCase, target, level) -> TicketPlans.repairBuilding(target));
        plans.put(BoardCase.REPAIR_TOOL, (boardCase, target, level) -> TicketPlans.repairTool(target));
        plans.put(BoardCase.STOCK_CHESTS, (boardCase, target, level) -> TicketFarmPlans.yard(target));
        plans.put(BoardCase.CRAFTING_STATION, (boardCase, target, level) -> TicketFarmPlans.tableNearSpirit(target));
        plans.put(
                BoardCase.CRAFT_ANVIL,
                (boardCase, target, level) -> TicketPlans.craft(NamespacedId.MINECRAFT + "anvil", 1, target));
        plans.put(
                BoardCase.CRAFT_SMITHING_TABLE,
                (boardCase, target, level) -> TicketPlans.craft(NamespacedId.MINECRAFT + "smithing_table", 1, target));
        plans.put(
                BoardCase.CRAFT_SMOKER,
                (boardCase, target, level) -> TicketPlans.craft(NamespacedId.MINECRAFT + "smoker", 1, target));
        plans.put(
                BoardCase.CRAFT_FURNACE,
                (boardCase, target, level) ->
                        TicketFarmPlans.placeInYard(NamespacedId.MINECRAFT + "furnace", 1, target));
        plans.put(BoardCase.CRAFT_TORCHES, (boardCase, target, level) -> TorchPlans.plan(target));
        plans.put(BoardCase.SMELT_CHARCOAL, (boardCase, target, level) -> TicketPlans.smeltCharcoal(target));
        for (BoardCase brew : List.of(
                BoardCase.GRIND_BLAZE_POWDER,
                BoardCase.BREW_POTION,
                BoardCase.HARVEST_NETHER_WART,
                BoardCase.CRAFT_BREWING_STAND)) {
            plans.put(brew, (boardCase, target, level) -> BrewPlans.plan(boardCase, target));
        }
        plans.put(BoardCase.FISH, (boardCase, target, level) -> TicketPlans.fishWorkPoint(target));
        for (BoardCase tool : List.of(
                BoardCase.CRAFT_WOODEN_PICKAXE,
                BoardCase.CRAFT_WOODEN_AXE,
                BoardCase.CRAFT_WOODEN_SHOVEL,
                BoardCase.CRAFT_STONE_PICKAXE,
                BoardCase.CRAFT_STONE_AXE,
                BoardCase.CRAFT_STONE_SHOVEL,
                BoardCase.CRAFT_STONE_SWORD,
                BoardCase.CRAFT_IRON_PICKAXE,
                BoardCase.CRAFT_IRON_AXE,
                BoardCase.CRAFT_IRON_SHOVEL,
                BoardCase.CRAFT_IRON_SWORD,
                BoardCase.CRAFT_DIAMOND_PICKAXE,
                BoardCase.CRAFT_DIAMOND_AXE,
                BoardCase.CRAFT_DIAMOND_SHOVEL,
                BoardCase.CRAFT_DIAMOND_SWORD)) {
            plans.put(tool, (boardCase, target, level) -> ToolCraftPlan.of(boardCase, target));
        }
        plans.put(
                BoardCase.CRAFT_SHEARS,
                (boardCase, target, level) -> TicketPlans.craft(WoolRules.CRAFTED_SHEARS, 1, target));
        plans.put(
                BoardCase.CRAFT_BED,
                (boardCase, target, level) -> TicketPlans.craft(
                        TicketPlans.bedItem(target), target.count() > 0 ? target.count() : 1, target));
        plans.put(
                BoardCase.WEAVE_WOOL,
                (boardCase, target, level) -> TicketPlans.craft(
                        target.craftItem() == null ? WoolRules.CRAFTED_WOOL : target.craftItem(),
                        target.count() > 0 ? target.count() : 1,
                        target));
        plans.put(BoardCase.SHEAR_SHEEP, (boardCase, target, level) -> TicketFarmPlans.shearSheep(target));
        plans.put(BoardCase.HUNT_WILD_SHEEP, (boardCase, target, level) -> TicketFarmPlans.huntWildSheep(target));
        plans.put(BoardCase.HARVEST_HIVE, (boardCase, target, level) -> TicketFarmPlans.harvestHive());
        plans.put(BoardCase.HARVEST_ORCHARD, (boardCase, target, level) -> OrchardPlans.harvest(target));
        plans.put(BoardCase.PLACE_BEEHIVE, (boardCase, target, level) -> TicketFarmPlans.placeBeehive(target));
        plans.put(BoardCase.BUILD_FENCE, (boardCase, target, level) -> TicketFarmPlans.buildFence(target, level));
        plans.put(BoardCase.LIGHTING, (boardCase, target, level) -> TorchPlans.lighting(target));
        plans.put(BoardCase.BUILD_STREETS, (boardCase, target, level) -> RoutePlans.path(target));
        plans.put(
                BoardCase.ENCHANT_SHELVES,
                (boardCase, target, level) -> TicketPlans.craft(NamespacedId.MINECRAFT + "bookshelf", 4, target));
        plans.put(BoardCase.DISMANTLE_SHELTER, (boardCase, target, level) -> TicketPlans.dismantleShelter(target));
        plans.put(BoardCase.BREED_ANIMALS, (boardCase, target, level) -> TicketFarmPlans.breedAnimals(target));
        plans.put(BoardCase.HERD_CULL, (boardCase, target, level) -> TicketFarmPlans.herdCull(target));
        plans.put(BoardCase.RELOCATE_BUILDING, (boardCase, target, level) -> TicketPlans.relocateBuilding(target));
        plans.put(BoardCase.RESTOCK_FOOD, (boardCase, target, level) -> TicketStoragePlans.restockFood(target));
        plans.put(BoardCase.TRANSFER_ITEM, (boardCase, target, level) -> TicketPlans.transferItem(target));
        plans.put(BoardCase.MINE_LEFT_ORE, (boardCase, target, level) -> TicketPlans.mineLeftOre(target));
        return Collections.unmodifiableMap(plans);
    }

    @FunctionalInterface
    private interface PlanFactory {
        TaskNode build(BoardCase boardCase, TicketTarget target, int level);
    }
}

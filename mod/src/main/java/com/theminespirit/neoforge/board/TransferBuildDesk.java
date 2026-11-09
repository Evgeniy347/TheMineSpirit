package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.social.TransferOrder;
import com.theminespirit.core.social.TransferPlanner;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.ItemIds;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

final class TransferBuildDesk {
    private static final String DIRT = "dirt";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TORCHES_PER_COAL = 4;

    private TransferBuildDesk() {}

    static boolean ensureBuildTransfers(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            BoardTicket build,
            SpiritEntity builder) {
        Map<String, Integer> need;
        try {
            TaskNode plan = TicketPlans.plan(build.boardCase(), build.level(), service.targetOf(build.id(), spirits));
            need = BuildRemainder.left(
                    level, build.boardCase(), plan, CaseMaterials.need(plan, FirstStepsRunner.materialSource(level)));
        } catch (RuntimeException failure) {
            LOGGER.warn("Материалы стройки {} для передачи не посчитаны: {}", build.id(), failure.getMessage());
            return false;
        }
        Map<String, Integer> wood = new HashMap<>();
        need.forEach((item, count) -> {
            if (!OwnWood.species(item).isEmpty() || OwnWood.stick(item)) {
                wood.put(item, count);
            }
        });
        BlockCoord yard = SettlementPlotStore.get(level)
                .site()
                .map(site -> site.workYard())
                .orElseGet(() -> builder.home().orElseGet(() -> TransferStockDesk.at(builder)));
        Map<String, Integer> pooled = TransferStockDesk.hasSettlementChest(level, yard)
                ? TransferStockDesk.chestStock(level, service, yard, build.id())
                : Map.of();
        if (!wood.isEmpty() && (ownWoodCovers(builder, wood) || pooledWoodCovers(builder, wood, pooled))) {
            need = new HashMap<>(need);
            need.keySet().removeAll(wood.keySet());
        }
        if (need.containsKey(DIRT) || need.containsKey("minecraft:" + DIRT)) {
            need = new HashMap<>(need);
            need.remove(DIRT);
            need.remove("minecraft:" + DIRT);
        }
        if (!pooled.isEmpty()) {
            Map<String, Integer> missing = new HashMap<>(need);
            missing.entrySet().removeIf(entry -> pooledCount(pooled, bareId(entry.getKey())) >= entry.getValue());
            need = missing;
        }
        int torches = need.getOrDefault("torch", 0) + need.getOrDefault("minecraft:torch", 0);
        int coal = builder.getInventory().countItem(Items.COAL)
                + builder.getInventory().countItem(Items.CHARCOAL)
                + pooledCount(pooled, "coal")
                + pooledCount(pooled, "charcoal")
                + (pooledCount(pooled, "torch") + TORCHES_PER_COAL - 1) / TORCHES_PER_COAL;
        if (torches > 0 && coal * TORCHES_PER_COAL >= torches) {
            need = new HashMap<>(need);
            need.remove("torch");
            need.remove("minecraft:torch");
        }
        List<Ingredient> materials = new ArrayList<>();
        need.forEach((item, count) -> materials.add(new Ingredient(ItemIds.normalize(item), count)));
        if (materials.isEmpty()) {
            return false;
        }
        StackMapper mapper = new StackMapper();
        RecipeKnowledge recipes = RecipeKnowledge.of(level);
        BlockCoord home = builder.home().orElseGet(() -> TransferStockDesk.at(builder));
        boolean chest = TransferStockDesk.hasSettlementChest(level, home);
        List<TransferOrder> orders = TransferPlanner.planForCraft(new TransferPlanner.CraftRequest(
                build.boardCase().name().toLowerCase(Locale.ROOT),
                builder.getUUID(),
                TransferStockDesk.at(builder),
                TransferStockDesk.stacks(builder.getInventory(), mapper),
                materials,
                TransferStockDesk.sources(
                        chest
                                ? spirits.stream()
                                        .filter(spirit -> !TransferWatch.busy(spirit))
                                        .toList()
                                : spirits,
                        mapper,
                        recipes,
                        builder.getUUID()),
                Map.of(),
                InventoryTables.values(),
                BoardTuning.transferRadiusBlocks(),
                BoardTuning.transferMaxCount()));
        if (TransferWatch.submitOrders(service, spirits, build, orders, level.getGameTime())) {
            return true;
        }
        return TransferCarriers.awaitsDelivery(service.board(), build.id());
    }

    static boolean ownWoodCovers(SpiritEntity crafter, Map<String, Integer> need) {
        String species = need.keySet().stream()
                .map(OwnWood::species)
                .filter(found -> !found.isEmpty())
                .findFirst()
                .orElse("");
        if (species.isEmpty()
                || !need.keySet().stream().allMatch(id -> OwnWood.stick(id) || species.equals(OwnWood.species(id)))) {
            return false;
        }
        int cost = need.entrySet().stream()
                .mapToInt(entry -> OwnWood.plankCost(entry.getKey(), entry.getValue()))
                .sum();
        return OwnWood.planksHeld(crafter.getInventory(), species) >= cost;
    }

    private static String bareId(String item) {
        return item.substring(item.indexOf(':') + 1);
    }

    private static int pooledCount(Map<String, Integer> pooled, String item) {
        return pooled.getOrDefault(item, 0) + pooled.getOrDefault("minecraft:" + item, 0);
    }

    private static boolean pooledWoodCovers(
            SpiritEntity builder, Map<String, Integer> need, Map<String, Integer> pooled) {
        String species = need.keySet().stream()
                .map(OwnWood::species)
                .filter(found -> !found.isEmpty())
                .findFirst()
                .orElse("");
        if (species.isEmpty()) {
            return false;
        }
        int cost = need.entrySet().stream()
                .mapToInt(entry -> OwnWood.plankCost(entry.getKey(), entry.getValue()))
                .sum();
        return OwnWood.planksHeld(builder.getInventory(), species) + OwnWood.planksIn(pooled, species) >= cost;
    }
}

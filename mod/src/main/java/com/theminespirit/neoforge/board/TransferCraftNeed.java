package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

final class TransferCraftNeed {
    private static final Logger LOGGER = LogUtils.getLogger();

    private TransferCraftNeed() {}

    static Map<String, Integer> gatherNeed(RecipeKnowledge recipes, String item, int count) {
        int output = Math.max(recipes.outputCount(item), 1);
        int crafts = (Math.max(count, 1) + output - 1) / output;
        Map<String, Integer> need = new LinkedHashMap<>();
        recipes.ingredientsFor(item)
                .forEach(ingredient -> need.merge(ingredient.item(), ingredient.count() * crafts, Integer::sum));
        return need;
    }

    static Map<String, Integer> craftNeed(
            RecipeKnowledge recipes, BoardTicket craftTicket, TicketTarget target, String item) {
        MaterialSource source = new MaterialSource() {
            @Override
            public int outputCount(String product) {
                return recipes.outputCount(product);
            }

            @Override
            public List<Ingredient> ingredientsFor(String product) {
                return recipes.ingredientsFor(product);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return Map.of();
            }
        };
        try {
            TaskNode plan = TicketPlans.plan(craftTicket.boardCase(), craftTicket.level(), target);
            Map<String, Integer> need = CaseMaterials.craftNeed(plan, source, item);
            if (!need.isEmpty()) {
                return need;
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("План крафта {} не собран для передачи: {}", craftTicket.id(), failure.getMessage());
        }
        Map<String, Integer> single = new LinkedHashMap<>();
        recipes.ingredientsFor(item)
                .forEach(ingredient -> single.merge(ingredient.item(), ingredient.count(), Integer::sum));
        return single;
    }

    static boolean placesOwnTable(BoardTicket craftTicket, TicketTarget target) {
        if (target.work() == null) {
            return false;
        }
        return placesTable(TicketPlans.plan(craftTicket.boardCase(), craftTicket.level(), target));
    }

    private static boolean placesTable(TaskNode plan) {
        return switch (plan) {
            case SequenceNode sequence -> sequence.steps().stream().anyMatch(TransferCraftNeed::placesTable);
            case FallbackNode fallback -> placesTable(fallback.steps().get(0));
            case ScriptCall call ->
                "place_block".equals(call.script())
                        && TransferWatch.TABLE_ITEM.equals(call.params().get("block"));
            default -> false;
        };
    }

    static boolean needsTable(ServerLevel level, RecipeKnowledge recipes, String item, BlockCoord craftAt) {
        return recipes.stationFor(item).filter(Stations.CRAFTING_TABLE::equals).isPresent()
                && !HomeBlocks.near(
                        level, craftAt, CatalogLimits.STATION_RADIUS_DEFAULT, state -> state.is(Blocks.CRAFTING_TABLE));
    }
}

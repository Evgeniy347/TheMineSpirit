package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.SchematicMaterials;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.BlockMatch;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.neoforge.OwnWood;
import com.theminespirit.neoforge.SchematicResources;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class StageMaterialDesk {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MINECRAFT = "minecraft:";
    private static final int EXPAND_DEPTH = 4;

    private StageMaterialDesk() {}

    static void dropOwnWood(Map<String, Integer> need, StockView stock, String wood) {
        int cost = 0;
        List<String> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : need.entrySet()) {
            if (wood.equals(OwnWood.species(entry.getKey()))) {
                cost += OwnWood.plankCost(entry.getKey(), entry.getValue());
                items.add(entry.getKey());
            }
        }
        if (!items.isEmpty() && OwnWood.planksIn(stock.available(), wood) >= cost) {
            items.forEach(need::remove);
        }
    }

    static void asPlanks(Map<String, Integer> need, String wood) {
        int cost = 0;
        for (Map.Entry<String, Integer> entry : List.copyOf(need.entrySet())) {
            if (wood.equals(OwnWood.species(entry.getKey()))) {
                cost += OwnWood.plankCost(entry.getKey(), entry.getValue());
                need.remove(entry.getKey());
            }
        }
        if (cost > 0) {
            need.merge(wood + "_planks", cost, Integer::sum);
        }
    }

    static Map<String, Integer> materialsOf(
            ServerLevel level, StockView stock, FirstStepsRunner.StageScan scan, BoardCase caseValue) {
        String wood = BuildingWoodChoice.stored(level, caseValue, scan.stage(), scan.wood());
        TicketTarget target = FirstStepsRunner.targetFor(level, caseValue, scan.stage(), wood);
        return need(level, stock, new FirstStepsRunner.OpenStep(caseValue, target, Map.of(), wood), false);
    }

    static Map<String, Integer> need(
            ServerLevel level, StockView stock, FirstStepsRunner.OpenStep step, boolean blocked) {
        BoardCase caseValue = step.caseValue();
        TicketTarget target = step.target();
        String wood = step.wood();
        MaterialSource source = materialSource(level);
        Map<String, Integer> need = new HashMap<>();
        if (caseValue == BoardCase.BUILD_MINE) {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            need.putAll(BuildGap.need(
                    plan, BuildGap.trace(plan, wood, FirstStepsRunner.probe(level), FirstStepsRunner.SCHEMES), source));
            StageMaterialNeed.mine(stock, need);
            return need;
        }
        if (caseValue == BoardCase.BUILD_ORCHARD) {
            return StageMaterialNeed.orchard(level, target, wood);
        }
        materials(source, caseValue, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        if (caseValue == BoardCase.CLEAR_GROUNDS) {
            need.keySet().removeIf(item -> "chest".equals(item) || PlotLeveling.DEFAULT_BLOCK.equals(item));
        }
        if (FirstStepsRunner.WOODEN_BUILDINGS.contains(caseValue)) {
            if (blocked) {
                asPlanks(need, wood);
            } else {
                dropOwnWood(need, stock, wood);
            }
        }
        return need;
    }

    static void expand(MaterialSource source, String item, int count, Map<String, Integer> raw, int depth) {
        List<Ingredient> ingredients = depth < EXPAND_DEPTH ? source.ingredientsFor(item) : List.of();
        int output = source.outputCount(item);
        if (ingredients.isEmpty() || output <= 0) {
            raw.merge(
                    item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item, count, Integer::sum);
            return;
        }
        int crafts = (count + output - 1) / output;
        for (Ingredient ingredient : ingredients) {
            expand(source, ingredient.item(), ingredient.count() * crafts, raw, depth + 1);
        }
    }

    static Map<String, Integer> materials(MaterialSource source, BoardCase caseValue, TicketTarget target) {
        try {
            TaskNode plan = TicketPlans.plan(caseValue, 1, target);
            return CaseMaterials.need(plan, source);
        } catch (RuntimeException failure) {
            LOGGER.error("Материалы шага первых шагов {} не посчитаны: {}", caseValue, failure.getMessage());
            return Map.of();
        }
    }

    static MaterialSource materialSource(ServerLevel level) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(level);
        return new MaterialSource() {
            @Override
            public int outputCount(String item) {
                return knowledge.outputCount(item);
            }

            @Override
            public List<Ingredient> ingredientsFor(String item) {
                return knowledge.ingredientsFor(item);
            }

            @Override
            public Map<String, Integer> schematicBlocks(String file) {
                return SchematicMaterials.count(
                        DataTables.INSTANCE.presets().current().byId(file).map(preset -> preset.type()),
                        () -> PresetLibrary.countMaterials(SchematicResources.load(file)));
            }
        };
    }

    static void minusPlaced(ServerLevel level, TaskNode plan, String wood, Map<String, Integer> need) {
        switch (plan) {
            case SequenceNode sequence -> sequence.steps().forEach(step -> minusPlaced(level, step, wood, need));
            case FallbackNode fallback -> minusPlaced(level, fallback.steps().get(0), wood, need);
            case ScriptCall call
            when SchemeAudit.entries(call).isPresent() -> {
                for (Schematic.PlacedEntry entry : SchemeAudit.entries(call).orElseThrow()) {
                    String block = bare(WoodSpecies.swap(entry.block(), wood));
                    BlockCoord at = entry.pos();
                    if (placed(level.getBlockState(new BlockPos(at.x(), at.y(), at.z())), block)) {
                        String item = bare(WoodSpecies.swap(SchematicBlocks.itemOf(entry.block()), wood));
                        need.computeIfPresent(item, (key, count) -> count > 1 ? count - 1 : null);
                    }
                }
            }
            case ScriptCall call
            when "place_block".equals(call.script())
                    && call.params().get("block") instanceof String block
                    && call.params().get("x") instanceof Integer x
                    && call.params().get("y") instanceof Integer y
                    && call.params().get("z") instanceof Integer z -> {
                String item = bare(WoodSpecies.swap(block, wood));
                if (placed(level.getBlockState(new BlockPos(x, y, z)), item)) {
                    need.computeIfPresent(item, (key, count) -> count > 1 ? count - 1 : null);
                }
            }
            default -> {}
        }
    }

    static String bare(String id) {
        int state = id.indexOf('[');
        String block = state < 0 ? id : id.substring(0, state);
        return block.startsWith(MINECRAFT) ? block.substring(MINECRAFT.length()) : block;
    }

    static boolean placed(BlockState state, String block) {
        return BlockMatch.matches(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), block);
    }
}

package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.FuelLoad;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public final class SmeltScript implements ScriptInstance {
    private static final String COLLECTED_KEY = "collected";

    private enum Phase {
        PREPARE,
        STATION,
        SMELT
    }

    private record Choice(String input, RecipeSpec recipe, int inputCount, int have) {
    }

    private final SpiritEntity spirit;
    private final SmeltParams params;
    private Phase phase = Phase.PREPARE;
    private String failureReason;
    private int collected;
    private Item input;
    private int inputCount;
    private int outputPerInput;
    private int cookTicks;
    private List<FuelLoad.Portion> fuel = List.of();
    private StationAccess station;
    private FurnaceSession session;

    public SmeltScript(SpiritEntity spirit, SmeltParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return session != null ? sessionStatus() : fail("дух исчез во время плавки");
        }
        return switch (phase) {
            case PREPARE -> prepare();
            case STATION -> useStation();
            case SMELT -> sessionStatus();
        };
    }

    @Override
    public void cancel() {
        if (station != null) {
            station.cancel();
        }
        if (session != null) {
            session.cancel();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        int total = collected + (session == null ? 0 : session.collected());
        return Map.of(COLLECTED_KEY, total);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(COLLECTED_KEY) instanceof Number number) {
            collected = Math.max(collected, number.intValue());
        }
    }

    private ScriptStatus prepare() {
        int remaining = params.count() - collected;
        if (remaining <= 0) {
            return ScriptStatus.SUCCEEDED;
        }
        String product = ItemIds.normalize(params.item());
        RecipeKnowledge knowledge = RecipeKnowledge.of(spirit.level());
        List<RecipeSpec> recipes = knowledge.smeltRecipes(product);
        if (recipes.isEmpty()) {
            return fail("нет рецепта плавки для '" + product + "'");
        }
        Map<String, Integer> available = CraftInventory.counts(spirit.getInventory());
        Choice choice = choose(recipes, remaining, available);
        if (choice.have() < choice.inputCount()) {
            return fail("не хватает: " + choice.input() + " " + (choice.inputCount() - choice.have()) + " из "
                    + choice.inputCount());
        }
        Optional<Item> inputItem = ItemIds.item(choice.input());
        if (inputItem.isEmpty()) {
            return fail("предмет '" + choice.input() + "' отсутствует в реестре");
        }
        input = inputItem.get();
        inputCount = choice.inputCount();
        outputPerInput = choice.recipe().outputCount();
        cookTicks = choice.recipe().cookTicks();
        fuel = FuelLoad.choose(inputCount, cookTicks, available, knowledge);
        if (fuel.isEmpty()) {
            return fail("нет топлива: в инвентаре нет ни одного топлива для плавки '" + product + "'");
        }
        station = new StationAccess(spirit, StationType.FURNACE, params.radius());
        phase = Phase.STATION;
        return ScriptStatus.RUNNING;
    }

    private static Choice choose(List<RecipeSpec> recipes, int remaining, Map<String, Integer> available) {
        Choice best = null;
        for (RecipeSpec recipe : recipes) {
            int needed = (remaining + recipe.outputCount() - 1) / recipe.outputCount();
            for (String alternative : recipe.ingredients().get(0).alternatives()) {
                int have = available.getOrDefault(alternative, 0);
                if (have >= needed) {
                    return new Choice(alternative, recipe, needed, have);
                }
                if (best == null || have > best.have()) {
                    best = new Choice(alternative, recipe, needed, have);
                }
            }
        }
        return best;
    }

    private ScriptStatus useStation() {
        return switch (station.tick()) {
            case WORKING -> ScriptStatus.RUNNING;
            case READY -> startSession(station.pos());
            case FAILED -> fail(station.failureReason());
        };
    }

    private ScriptStatus startSession(BlockPos furnace) {
        session = new FurnaceSession(spirit, furnace, input, inputCount, outputPerInput, fuel, cookTicks, 0);
        if (session.holdsForeignItems()) {
            return fail("печь в " + furnace.toShortString() + " занята: в ней лежат чужие предметы");
        }
        phase = Phase.SMELT;
        return sessionStatus();
    }

    private ScriptStatus sessionStatus() {
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> fail(session.failureReason());
        };
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

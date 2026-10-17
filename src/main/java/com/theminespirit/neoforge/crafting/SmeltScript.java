package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.FuelLoad;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.SmeltParams;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

public final class SmeltScript implements ScriptInstance {
    private static final String COLLECTED_KEY = "collected";
    private static final String FURNACE_X_KEY = "furnaceX";
    private static final String FURNACE_Y_KEY = "furnaceY";
    private static final String FURNACE_Z_KEY = "furnaceZ";

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
    private BlockPos resumeFurnace;
    private FurnaceSession.Job job;
    private StationAccess station;
    private FurnaceSession session;
    private final List<StationType> candidateTypes;
    private int stationIndex;

    public SmeltScript(SpiritEntity spirit, SmeltParams params) {
        this.spirit = spirit;
        this.params = params;
        String product = ItemIds.normalize(params.item());
        this.candidateTypes = Stations.isFoodProduct(product)
                ? List.of(StationType.SMOKER, StationType.FURNACE)
                : List.of(StationType.FURNACE);
        this.stationIndex = 0;
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
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(COLLECTED_KEY, session == null ? collected : session.collected());
        BlockPos furnace = session == null ? resumeFurnace : session.pos();
        if (furnace != null) {
            state.put(FURNACE_X_KEY, furnace.getX());
            state.put(FURNACE_Y_KEY, furnace.getY());
            state.put(FURNACE_Z_KEY, furnace.getZ());
        }
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(COLLECTED_KEY) instanceof Number number) {
            collected = Math.max(collected, number.intValue());
        }
        if (state.get(FURNACE_X_KEY) instanceof Number x && state.get(FURNACE_Y_KEY) instanceof Number y
                && state.get(FURNACE_Z_KEY) instanceof Number z) {
            resumeFurnace = new BlockPos(x.intValue(), y.intValue(), z.intValue());
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
        Optional<FurnaceSession.Contents> inside = resumeFurnace == null ? Optional.empty()
                : FurnaceSession.contentsAt(spirit.level(), resumeFurnace);
        FurnaceSession.Contents resident = inside.orElse(new FurnaceSession.Contents(0, 0, 0));
        Choice choice = choose(recipes, remaining, resident, available);
        if (choice.have() < choice.inputCount()) {
            return fail("не хватает: " + choice.input() + " " + (choice.inputCount() - choice.have()) + " из "
                    + choice.inputCount());
        }
        Optional<Item> inputItem = ItemIds.item(choice.input());
        if (inputItem.isEmpty()) {
            return fail("предмет '" + choice.input() + "' отсутствует в реестре");
        }
        int cookTicks = choice.recipe().cookTicks();
        int inPlay = choice.inputCount() + resident.input();
        List<FuelLoad.Portion> fuel = inPlay > 0 ? FuelLoad.choose(inPlay, cookTicks, available, knowledge)
                : List.of();
        if (fuel.isEmpty() && inPlay > 0 && resident.fuel() == 0) {
            return fail("нет топлива: в инвентаре нет ни одного топлива для плавки '" + product + "'");
        }
        job = new FurnaceSession.Job(inputItem.get(), choice.inputCount(), choice.recipe().outputCount(), fuel,
                cookTicks, inPlay, collected, params.count());
        if (inside.isPresent()) {
            return startSession(resumeFurnace, true);
        }
        station = new StationAccess(spirit, candidateTypes.get(stationIndex), params.radius());
        phase = Phase.STATION;
        return ScriptStatus.RUNNING;
    }

    private static Choice choose(List<RecipeSpec> recipes, int remaining, FurnaceSession.Contents resident,
            Map<String, Integer> available) {
        Choice best = null;
        for (RecipeSpec recipe : recipes) {
            int perInput = recipe.outputCount();
            int stillToProduce = Math.max(0, remaining - resident.result() - resident.input() * perInput);
            int needed = (stillToProduce + perInput - 1) / perInput;
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
            case READY -> startSession(station.pos(), false);
            case FAILED -> {
                if (stationIndex + 1 < candidateTypes.size()) {
                    stationIndex++;
                    station = new StationAccess(spirit, candidateTypes.get(stationIndex), params.radius());
                    phase = Phase.STATION;
                    yield ScriptStatus.RUNNING;
                }
                yield fail(station.failureReason());
            }
        };
    }

    private ScriptStatus startSession(BlockPos furnace, boolean adopted) {
        session = new FurnaceSession(spirit, furnace, job);
        if (!adopted && FurnaceSession.contentsAt(spirit.level(), furnace).map(c -> !c.isEmpty()).orElse(false)) {
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

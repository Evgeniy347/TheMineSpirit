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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;

public final class SmeltScript implements ScriptInstance {
    private static final String COLLECTED_KEY = "collected";
    private static final String STATION_X_KEY = "furnaceX";
    private static final String STATION_Y_KEY = "furnaceY";
    private static final String STATION_Z_KEY = "furnaceZ";

    private enum Phase {
        PREPARE,
        STATION,
        SMELT,
        CAMPFIRE
    }

    private record Choice(String input, RecipeSpec recipe, int inputCount, int have) {}

    private final SpiritEntity spirit;
    private final SmeltParams params;
    private final List<StationType> candidateTypes;
    private Phase phase = Phase.PREPARE;
    private String failureReason;
    private String product;
    private int collected;
    private BlockPos resumeStation;
    private RecipeKnowledge knowledge;
    private Choice choice;
    private Item inputItem;
    private FurnaceSession.Contents resident = new FurnaceSession.Contents(0, 0, 0);
    private int inPlay;
    private StationAccess station;
    private int stationIndex;
    private FurnaceSession furnace;
    private CampfireSession campfire;

    public SmeltScript(SpiritEntity spirit, SmeltParams params) {
        this.spirit = spirit;
        this.params = params;
        this.product = ItemIds.normalize(params.item());
        this.candidateTypes = Stations.smeltingCandidates(product).stream()
                .map(StationType::byId)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            if (campfire != null) {
                return campfireStatus();
            }
            return furnace != null ? furnaceStatus() : fail("дух исчез во время плавки");
        }
        return switch (phase) {
            case PREPARE -> prepare();
            case STATION -> useStation();
            case SMELT -> furnaceStatus();
            case CAMPFIRE -> campfireStatus();
        };
    }

    @Override
    public void cancel() {
        if (station != null) {
            station.cancel();
        }
        if (furnace != null) {
            furnace.cancel();
        }
        if (campfire != null) {
            campfire.cancel();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(
                COLLECTED_KEY,
                campfire != null ? campfire.collected() : furnace == null ? collected : furnace.collected());
        BlockPos pos = campfire != null ? campfire.pos() : furnace == null ? resumeStation : furnace.pos();
        if (pos != null) {
            state.put(STATION_X_KEY, pos.getX());
            state.put(STATION_Y_KEY, pos.getY());
            state.put(STATION_Z_KEY, pos.getZ());
        }
        return state;
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(COLLECTED_KEY) instanceof Number number) {
            collected = Math.max(collected, number.intValue());
        }
        if (state.get(STATION_X_KEY) instanceof Number x
                && state.get(STATION_Y_KEY) instanceof Number y
                && state.get(STATION_Z_KEY) instanceof Number z) {
            resumeStation = new BlockPos(x.intValue(), y.intValue(), z.intValue());
        }
    }

    private ScriptStatus prepare() {
        int remaining = params.count() - collected;
        if (remaining <= 0) {
            return ScriptStatus.SUCCEEDED;
        }
        knowledge = RecipeKnowledge.of(spirit.level());
        List<RecipeSpec> recipes = knowledge.smeltRecipes(product);
        if (recipes.isEmpty()) {
            return fail("нет рецепта плавки для '" + product + "'");
        }
        Map<String, Integer> available = CraftInventory.counts(spirit.getInventory());
        Optional<FurnaceSession.Contents> inside =
                resumeStation == null ? Optional.empty() : stationContentsAt(spirit.level(), resumeStation, product);
        resident = inside.orElse(new FurnaceSession.Contents(0, 0, 0));
        choice = choose(recipes, remaining, resident, available);
        if (choice.have() < choice.inputCount()) {
            return fail("не хватает: " + choice.input() + " " + (choice.inputCount() - choice.have()) + " из "
                    + choice.inputCount());
        }
        Optional<Item> found = ItemIds.item(choice.input());
        if (found.isEmpty()) {
            return fail("предмет '" + choice.input() + "' отсутствует в реестре");
        }
        inputItem = found.get();
        inPlay = choice.inputCount() + resident.input();
        if (inside.isPresent()) {
            BlockEntity entity = spirit.level().getBlockEntity(resumeStation);
            if (entity instanceof CampfireBlockEntity) {
                return startCampfire(resumeStation, true);
            }
            return startFurnace(resumeStation, true);
        }
        resumeStation = null;
        station = newStation(candidateTypes.get(stationIndex));
        phase = Phase.STATION;
        return ScriptStatus.RUNNING;
    }

    private static Optional<FurnaceSession.Contents> stationContentsAt(Level level, BlockPos pos, String product) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof CampfireBlockEntity campfire) {
            if (!Stations.isFoodProduct(product)) {
                return Optional.empty();
            }
            int onFire = 0;
            for (ItemStack stack : campfire.getItems()) {
                onFire += stack.getCount();
            }
            return Optional.of(new FurnaceSession.Contents(onFire, 0, 0));
        }
        return FurnaceSession.contentsAt(level, pos);
    }

    private static Choice choose(
            List<RecipeSpec> recipes, int remaining, FurnaceSession.Contents resident, Map<String, Integer> available) {
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

    private StationAccess newStation(StationType type) {
        if (type == StationType.CAMPFIRE) {
            return new StationAccess(spirit, type, params.radius(), CampfireKit::ensure);
        }
        return new StationAccess(spirit, type, params.radius());
    }

    private ScriptStatus useStation() {
        return switch (station.tick()) {
            case WORKING -> ScriptStatus.RUNNING;
            case READY ->
                candidateTypes.get(stationIndex) == StationType.CAMPFIRE
                        ? startCampfire(station.pos(), false)
                        : startFurnace(station.pos(), false);
            case FAILED -> {
                if (stationIndex + 1 < candidateTypes.size()) {
                    stationIndex++;
                    station = newStation(candidateTypes.get(stationIndex));
                    phase = Phase.STATION;
                    yield ScriptStatus.RUNNING;
                }
                yield fail(station.failureReason());
            }
        };
    }

    private ScriptStatus startFurnace(BlockPos pos, boolean adopted) {
        Map<String, Integer> available = CraftInventory.counts(spirit.getInventory());
        List<FuelLoad.Portion> fuel =
                inPlay > 0 ? FuelLoad.choose(inPlay, choice.recipe().cookTicks(), available, knowledge) : List.of();
        if (fuel.isEmpty() && inPlay > 0 && resident.fuel() == 0) {
            return fail("нет топлива: в инвентаре нет ни одного топлива для плавки '" + product + "'");
        }
        if (!adopted
                && FurnaceSession.contentsAt(spirit.level(), pos)
                        .map(c -> !c.isEmpty())
                        .orElse(false)) {
            return fail("печь в " + pos.toShortString() + " занята: в ней лежат чужие предметы");
        }
        FurnaceSession.Job job = new FurnaceSession.Job(
                inputItem,
                choice.inputCount(),
                choice.recipe().outputCount(),
                fuel,
                choice.recipe().cookTicks(),
                inPlay,
                collected,
                params.count());
        furnace = new FurnaceSession(spirit, pos, job);
        phase = Phase.SMELT;
        return furnaceStatus();
    }

    private ScriptStatus startCampfire(BlockPos pos, boolean adopted) {
        if (!adopted && occupiedCampfire(pos)) {
            return fail("костёр в " + pos.toShortString() + " занят: на нём лежит чужая еда");
        }
        SingleRecipeInput sample = new SingleRecipeInput(new ItemStack(inputItem));
        Optional<RecipeHolder<CampfireCookingRecipe>> found =
                spirit.level().getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, sample, spirit.level());
        if (found.isEmpty()) {
            return fail("нет рецепта готовки на костре для '" + product + "'");
        }
        ItemStack result = found.get().value().assemble(sample, spirit.level().registryAccess());
        if (result.isEmpty()) {
            return fail("рецепт готовки на костре для '" + product + "' не даёт предмета");
        }
        int cookTicks = found.get().value().getCookingTime();
        campfire = new CampfireSession(
                spirit,
                pos,
                new CampfireSession.Job(
                        inputItem, choice.inputCount(), result.getItem(), cookTicks, collected, params.count()));
        phase = Phase.CAMPFIRE;
        return campfireStatus();
    }

    private boolean occupiedCampfire(BlockPos pos) {
        BlockEntity entity = spirit.level().getBlockEntity(pos);
        if (entity instanceof CampfireBlockEntity campfire) {
            return campfire.getItems().stream().anyMatch(stack -> !stack.isEmpty());
        }
        return false;
    }

    private ScriptStatus furnaceStatus() {
        return switch (furnace.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> fail(furnace.failureReason());
        };
    }

    private ScriptStatus campfireStatus() {
        return switch (campfire.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> fail(campfire.failureReason());
        };
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

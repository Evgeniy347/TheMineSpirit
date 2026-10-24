package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.crafting.CraftAssembly;
import com.theminespirit.core.crafting.CraftPlan;
import com.theminespirit.core.crafting.CraftTuning;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.CraftParams;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlacementJournal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class CraftScript implements ScriptInstance {
    private static final String CRAFTED_KEY = "crafted";
    private static final int PLACE_REACH = 3;
    private static final int PLACE_PASSES = 3;

    private enum Phase {
        PREPARE,
        STATION,
        CRAFT,
        PLACE
    }

    private record Step(List<Item> consumed, List<ItemStack> produced, int output, boolean target) {}

    private final SpiritEntity spirit;
    private final CraftParams params;
    private Phase phase = Phase.PREPARE;
    private String failureReason;
    private int crafted;
    private List<Step> steps = List.of();
    private int nextStep;
    private int ticksSinceCraft = CraftTuning.CRAFT_INTERVAL_TICKS;
    private StationAccess station;
    private StationFinder placedFinder;

    public CraftScript(SpiritEntity spirit, CraftParams params) {
        this.spirit = spirit;
        this.params = params;
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            return fail("дух исчез во время крафта");
        }
        return switch (phase) {
            case PREPARE -> prepare();
            case STATION -> useStation();
            case CRAFT -> finish(craft());
            case PLACE -> place();
        };
    }

    @Override
    public void cancel() {
        if (station != null) {
            station.cancel();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return Map.of(CRAFTED_KEY, crafted);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (state.get(CRAFTED_KEY) instanceof Number number) {
            crafted = Math.max(crafted, number.intValue());
        }
    }

    private ScriptStatus prepare() {
        int remaining = params.count() - crafted;
        if (remaining <= 0) {
            return finish(ScriptStatus.SUCCEEDED);
        }
        String name = ItemIds.normalize(params.item());
        RecipeKnowledge knowledge = RecipeKnowledge.of(spirit.level());
        Map<String, Integer> available = CraftInventory.counts(spirit.getInventory());
        Optional<RecipeSpec> found = knowledge.craftRecipe(name, available);
        if (found.isEmpty()) {
            return fail("нет рецепта для '" + name + "' (динамические рецепты не поддерживаются)");
        }
        RecipeSpec spec = found.get();
        List<CraftAssembly.Order> orders = orders(spec, name, remaining, available, knowledge);
        if (orders.isEmpty()) {
            return fail("не хватает: " + shortage(CraftPlan.of(spec, remaining, available)));
        }
        Optional<String> refusal = buildSteps(orders, name);
        if (refusal.isPresent()) {
            return fail(refusal.get());
        }
        RecipeSpec finalRecipe = orders.get(orders.size() - 1).recipe();
        Optional<StationType> needed = Stations.forRecipe(finalRecipe).flatMap(StationType::byId);
        if (needed.isEmpty()) {
            phase = Phase.CRAFT;
            return finish(craft());
        }
        station = new StationAccess(spirit, needed.get(), params.radius());
        phase = Phase.STATION;
        return ScriptStatus.RUNNING;
    }

    private static List<CraftAssembly.Order> orders(
            RecipeSpec spec, String name, int remaining, Map<String, Integer> available, RecipeKnowledge knowledge) {
        CraftPlan plan = CraftPlan.of(spec, remaining, available);
        if (plan.feasible()) {
            List<CraftAssembly.Order> direct = new ArrayList<>(plan.crafts());
            for (int craft = 0; craft < plan.crafts(); craft++) {
                direct.add(new CraftAssembly.Order(plan.recipe(), plan.itemsForCraft(craft)));
            }
            return direct;
        }
        Map<String, Integer> stock = new HashMap<>(available);
        stock.put(name, 0);
        return CraftAssembly.plan(name, remaining, stock, knowledge.index()).orElse(List.of());
    }

    private Optional<String> buildSteps(List<CraftAssembly.Order> orders, String targetName) {
        List<Step> built = new ArrayList<>(orders.size());
        SimpleContainer simulation = CraftInventory.copyOf(spirit.getInventory());
        for (CraftAssembly.Order order : orders) {
            Optional<CraftingRecipe> found = vanillaRecipe(order.recipe().id());
            if (found.isEmpty()) {
                return Optional.of("рецепт '" + order.recipe().id() + "' исчез из датапаков во время крафта");
            }
            CraftingRecipe recipe = found.get();
            List<Item> items = new ArrayList<>();
            for (String id : order.items()) {
                Optional<Item> item = ItemIds.item(id);
                if (item.isEmpty()) {
                    return Optional.of("предмет '" + id + "' рецепта отсутствует в реестре");
                }
                items.add(item.get());
            }
            CraftingInput input = gridFor(recipe, items);
            if (!recipe.matches(input, spirit.level())) {
                return Optional.of("рецепт '" + order.recipe().id() + "' не принял выбранные предметы " + items);
            }
            List<ItemStack> produced = new ArrayList<>();
            produced.add(recipe.assemble(input, spirit.level().registryAccess()));
            for (ItemStack leftover : recipe.getRemainingItems(input)) {
                if (!leftover.isEmpty()) {
                    produced.add(leftover);
                }
            }
            if (!CraftInventory.apply(simulation, items, produced)) {
                return Optional.of("инвентарь полон: не помещаются результат и остатки крафта '" + params.item() + "'");
            }
            built.add(new Step(
                    items,
                    produced,
                    produced.get(0).getCount(),
                    targetName.equals(order.recipe().outputItem())));
        }
        steps = built;
        return Optional.empty();
    }

    private CraftingInput gridFor(CraftingRecipe recipe, List<Item> items) {
        if (recipe instanceof ShapedRecipe shaped) {
            NonNullList<Ingredient> pattern = shaped.getIngredients();
            List<ItemStack> grid = new ArrayList<>(pattern.size());
            int next = 0;
            for (Ingredient ingredient : pattern) {
                grid.add(ingredient.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get(next++)));
            }
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), grid);
        }
        List<ItemStack> grid = new ArrayList<>(items.size());
        items.forEach(item -> grid.add(new ItemStack(item)));
        return CraftingInput.of(items.size(), 1, grid);
    }

    private Optional<CraftingRecipe> vanillaRecipe(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Optional.empty();
        }
        return spirit.level()
                .getRecipeManager()
                .byKey(key)
                .map(RecipeHolder::value)
                .filter(CraftingRecipe.class::isInstance)
                .map(CraftingRecipe.class::cast);
    }

    private ScriptStatus useStation() {
        return switch (station.tick()) {
            case WORKING -> ScriptStatus.RUNNING;
            case READY -> {
                phase = Phase.CRAFT;
                yield finish(craft());
            }
            case FAILED -> fail(station.failureReason());
        };
    }

    private ScriptStatus craft() {
        if (nextStep >= steps.size()) {
            return ScriptStatus.SUCCEEDED;
        }
        if (station != null && !station.stillStanding()) {
            return fail("станция в " + station.pos().toShortString() + " исчезла: скрафчено " + crafted + " из "
                    + params.count());
        }
        ticksSinceCraft++;
        if (ticksSinceCraft < CraftTuning.CRAFT_INTERVAL_TICKS) {
            return ScriptStatus.RUNNING;
        }
        Step step = steps.get(nextStep);
        SimpleContainer simulation = CraftInventory.copyOf(spirit.getInventory());
        if (!CraftInventory.apply(simulation, step.consumed(), step.produced())) {
            return fail("инвентарь изменился или полон: скрафчено " + crafted + " из " + params.count());
        }
        CraftInventory.apply(spirit.getInventory(), step.consumed(), step.produced());
        ticksSinceCraft = 0;
        nextStep++;
        if (step.target()) {
            crafted += step.output();
        }
        return nextStep >= steps.size() ? ScriptStatus.SUCCEEDED : ScriptStatus.RUNNING;
    }

    // Скрафченная станция с place ставится рядом, если такой поблизости нет (печь первых шагов — у двора станций).
    private ScriptStatus finish(ScriptStatus status) {
        if (status != ScriptStatus.SUCCEEDED || !params.place()) {
            return status;
        }
        Optional<StationType> type = StationType.byId(params.item());
        if (type.isEmpty()) {
            return placeBlocks();
        }
        placedFinder = new StationFinder(spirit.level(), spirit.blockPosition(), params.radius(), type.get());
        phase = Phase.PLACE;
        return ScriptStatus.RUNNING;
    }

    private ScriptStatus place() {
        if (!placedFinder.step()) {
            return ScriptStatus.RUNNING;
        }
        if (placedFinder.found().isPresent()) {
            return ScriptStatus.SUCCEEDED;
        }
        StationType type = StationType.byId(params.item()).orElseThrow();
        if (StationPlacer.place(spirit, type).isEmpty()) {
            // Вплотную к духу места нет (двор занят верстаком и сундуками) — клетка в шаге досягаемости: печь
            // оставалась в инвентаре, шаг падал, а запас «с печью» закрывал CRAFT_FURNACE без записи в журнале
            // (приёмка «джунгли» 24.10: «нет [CRAFT_FURNACE]»).
            return placeBlocks();
        }
        return ScriptStatus.SUCCEEDED;
    }

    // Не станция (сундуки склада первых шагов): скрафченное ставится рядом с духом, не вплотную к другому сундуку.
    // Сундуки оставались в инвентаре крафтера — на участке их не было, и сдача добытого не находила склад
    // (приёмка «джунгли» 24.10: «сундуки []»).
    private ScriptStatus placeBlocks() {
        Optional<Item> item = ItemIds.item(ItemIds.normalize(params.item()));
        if (item.isEmpty() || !(item.get() instanceof BlockItem blockItem)) {
            return fail("place: '" + params.item() + "' не блок — ставить нечего");
        }
        int left = Math.min(crafted, spirit.getInventory().countItem(blockItem));
        BlockPlacer placer = new BlockPlacer(spirit);
        BlockState state = blockItem.getBlock().defaultBlockState();
        String id = "minecraft:" + ItemIds.normalize(params.item());
        BlockPos origin = spirit.blockPosition();
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -PLACE_REACH; dx <= PLACE_REACH; dx++) {
            for (int dz = -PLACE_REACH; dz <= PLACE_REACH; dz++) {
                if (dx != 0 || dz != 0) {
                    cells.add(origin.offset(dx, 0, dz));
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(origin))
                .thenComparing(Comparator.naturalOrder()));
        Level level = spirit.level();
        // Сундук — сначала второй половиной к одиночному сундуку рядом, затем первым так, чтобы рядом было место второй
        // половине: два сундука склада — двойной (INC-52).
        while (left > 0) {
            Optional<BlockPos> placed = Optional.empty();
            for (int pass = 0; pass < PLACE_PASSES && placed.isEmpty(); pass++) {
                placed = placeOnce(level, placer, cells, state, id, pass, left);
            }
            if (placed.isEmpty()) {
                break;
            }
            left--;
        }
        if (left > 0) {
            return fail("негде поставить '" + params.item() + "': осталось " + left
                    + ", вокруг духа нет свободной клетки на твёрдом полу");
        }
        return ScriptStatus.SUCCEEDED;
    }

    // Проход 0 — вторая половина к одиночному сундуку; 1 — одиночный с местом для второй половины (сундуков ещё
    // больше одного); 2 — любая свободная клетка.
    private Optional<BlockPos> placeOnce(
            Level level, BlockPlacer placer, List<BlockPos> cells, BlockState state, String id, int pass, int left) {
        for (BlockPos cell : cells) {
            Optional<BlockState> pair = ChestPairing.paired(level, cell, state);
            boolean wanted = pass == 0
                    ? pair.isPresent()
                    : pair.isEmpty() && (pass == 2 || (left > 1 && partnerFree(level, cells, cell, state)));
            if (wanted
                    && freeCell(level, cell, state, pair.isPresent())
                    && placer.begin(cell, pair.orElse(state), id) instanceof BlockPlacer.Started) {
                PlacementJournal.placed(
                        spirit, id, cell, "скрафчено с place" + (pair.isPresent() ? ", вторая половина двойного" : ""));
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    private static boolean partnerFree(Level level, List<BlockPos> cells, BlockPos cell, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        for (BlockPos partner :
                List.of(cell.relative(facing.getClockWise()), cell.relative(facing.getCounterClockWise()))) {
            if (cells.contains(partner) && freeCell(level, partner, state, false)) {
                return true;
            }
        }
        return false;
    }

    // paired — клетка второй половины двойного сундука: соседний сундук там и нужен, второй сосед — нет.
    private static boolean freeCell(Level level, BlockPos cell, BlockState state, boolean paired) {
        BlockPos floor = cell.below();
        if (!level.getBlockState(cell).isAir()
                || !level.getBlockState(cell.above()).isAir()
                || !level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            return false;
        }
        int neighbours = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(cell.relative(side)).is(state.getBlock())) {
                neighbours++;
            }
        }
        return neighbours == (paired ? 1 : 0);
    }

    private static String shortage(CraftPlan plan) {
        StringJoiner joiner = new StringJoiner(", ");
        plan.missing()
                .forEach((item, lack) ->
                        joiner.add(item + " " + lack + " из " + plan.required().get(item)));
        return joiner.toString();
    }

    private ScriptStatus fail(String reason) {
        failureReason = reason;
        return ScriptStatus.FAILED;
    }
}

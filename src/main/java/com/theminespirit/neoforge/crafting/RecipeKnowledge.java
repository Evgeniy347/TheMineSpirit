package com.theminespirit.neoforge.crafting;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.crafting.IngredientSpec;
import com.theminespirit.core.crafting.RecipeChooser;
import com.theminespirit.core.crafting.RecipeCurator;
import com.theminespirit.core.crafting.RecipeIndex;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.crafting.Stations;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.mining.GatherPolicy;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

public final class RecipeKnowledge implements ResourceKnowledge {
    private static final AtomicInteger INDEX_BUILDS = new AtomicInteger();
    private static volatile Cached cache;

    private final RecipeIndex index;
    private final Map<String, Integer> available;
    private final String species;

    private RecipeKnowledge(RecipeIndex index, Map<String, Integer> available, String species) {
        this.index = index;
        this.available = available;
        this.species = species;
    }

    // Те же рецепты, но вариант рецепта и слота (порода досок, брёвен) — по запасу духа и породе поселения, а не
    // первый в общем порядке: в джунглях acquire палок и сундука брал дубовые доски, которых не добыть.
    public RecipeKnowledge preferring(Map<String, Integer> stock, String woodSpecies) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        return new RecipeKnowledge(index, Map.copyOf(stock), WoodSpecies.check(woodSpecies));
    }

    public static RecipeKnowledge of(Level level) {
        return of(level.getRecipeManager(), level.registryAccess());
    }

    public static RecipeKnowledge of(RecipeManager manager, HolderLookup.Provider registries) {
        Objects.requireNonNull(manager, "manager не может быть null: рецепты берутся у сервера");
        Cached current = cache;
        if (current == null || !manager.equals(current.manager())) {
            current = new Cached(manager, buildIndex(manager, registries));
            cache = current;
        }
        return new RecipeKnowledge(current.index(), Map.of(), WoodSpecies.OAK);
    }

    public static int indexBuilds() {
        return INDEX_BUILDS.get();
    }

    public RecipeIndex index() {
        return index;
    }

    @Override
    public boolean isGatherable(String item) {
        String id = ItemIds.normalize(item);
        if (DropTable.shared()
                .blocksFor(id)
                .map(blocks -> blocks.stream().anyMatch(RecipeKnowledge::gatherableBlock))
                .orElse(false)) {
            return true;
        }
        return ownBlock(id)
                .map(block -> GatherPolicy.check(block.toString()) instanceof GatherPolicy.Allowed)
                .orElse(false);
    }

    @Override
    public String gatherBlockFor(String item) {
        String id = ItemIds.normalize(item);
        return DropTable.shared()
                .blocksFor(id)
                .flatMap(blocks ->
                        blocks.stream().filter(RecipeKnowledge::gatherableBlock).findFirst())
                .orElse(id);
    }

    @Override
    public Optional<String> toolForBlock(String block) {
        String qualified = block.indexOf(':') < 0 ? "minecraft:" + block : block;
        ResourceLocation location = ResourceLocation.tryParse(qualified);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(location).flatMap(RecipeKnowledge::requiredPickaxe);
    }

    private static Optional<String> requiredPickaxe(Block block) {
        BlockState state = block.defaultBlockState();
        if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return Optional.empty();
        }
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return Optional.of("diamond_pickaxe");
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return Optional.of("iron_pickaxe");
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return Optional.of("stone_pickaxe");
        }
        return Optional.of("wooden_pickaxe");
    }

    private static Optional<ResourceLocation> ownBlock(String id) {
        return ItemIds.item(id).map(BuiltInRegistries.ITEM::getKey).filter(BuiltInRegistries.BLOCK::containsKey);
    }

    private static boolean gatherableBlock(String blockId) {
        String qualified = blockId.indexOf(':') < 0 ? "minecraft:" + blockId : blockId;
        ResourceLocation location = ResourceLocation.tryParse(qualified);
        return location != null
                && BuiltInRegistries.BLOCK.containsKey(location)
                && GatherPolicy.check(qualified) instanceof GatherPolicy.Allowed;
    }

    @Override
    public boolean isCraftable(String item) {
        return !index.craftsFor(ItemIds.normalize(item)).isEmpty();
    }

    @Override
    public int outputCount(String item) {
        return craftRecipe(item, available).map(RecipeSpec::outputCount).orElse(1);
    }

    @Override
    public List<Ingredient> ingredientsFor(String item) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        craftRecipe(item, available)
                .ifPresent(recipe ->
                        recipe.ingredients().forEach(slot -> totals.merge(representative(slot), 1, Integer::sum)));
        List<Ingredient> ingredients = new ArrayList<>(totals.size());
        totals.forEach((name, count) -> ingredients.add(new Ingredient(name, count)));
        return List.copyOf(ingredients);
    }

    @Override
    public boolean isSmeltable(String item) {
        return !index.smeltingFor(ItemIds.normalize(item)).isEmpty();
    }

    @Override
    public String smeltInputFor(String item) {
        List<String> alternatives = new ArrayList<>();
        for (RecipeSpec recipe : smeltRecipes(item)) {
            alternatives.addAll(recipe.ingredients().get(0).alternatives());
        }
        return pickRepresentative(alternatives);
    }

    @Override
    public Optional<String> stationFor(String item) {
        Optional<RecipeSpec> craft = craftRecipe(item, available);
        if (craft.isPresent()) {
            return Stations.forRecipe(craft.get());
        }
        return smeltRecipes(item).stream().findFirst().flatMap(Stations::forRecipe);
    }

    @Override
    public int burnTicks(String item) {
        return ItemIds.item(ItemIds.normalize(item))
                .map(RecipeKnowledge::dataMapBurnTicks)
                .orElse(0);
    }

    @SuppressWarnings(
            "deprecation") // ванильный геттер помечен @Deprecated, но data map NeoForge читается только через него
    private static int dataMapBurnTicks(Item item) {
        FurnaceFuel fuel = item.builtInRegistryHolder().getData(NeoForgeDataMaps.FURNACE_FUELS);
        return fuel == null ? 0 : fuel.burnTime();
    }

    public Optional<RecipeSpec> craftRecipe(String item, Map<String, Integer> available) {
        return RecipeChooser.pick(index.craftsFor(ItemIds.normalize(item)), available);
    }

    public List<RecipeSpec> smeltRecipes(String product) {
        return index.smeltingFor(ItemIds.normalize(product)).stream()
                .sorted(Comparator.comparing(RecipeSpec::id))
                .toList();
    }

    private String representative(IngredientSpec slot) {
        // Вариант из запаса добывать не надо — проверка добываемости только для породы поселения: полный обход
        // рецептов для каждого предмета запаса (каменные кирпичи…) вешал серверный поток (приёмка «джунгли»).
        Optional<String> preferred = WoodSpecies.prefer(slot.alternatives(), available, species)
                .filter(alternative -> held(alternative) || canObtain(alternative, new HashSet<>()));
        if (preferred.isPresent()) {
            return preferred.get();
        }
        return pickRepresentative(slot.alternatives());
    }

    private boolean held(String item) {
        String name = ItemIds.normalize(item);
        return available.getOrDefault(name, 0) > 0 || available.getOrDefault("minecraft:" + name, 0) > 0;
    }

    private String pickRepresentative(List<String> alternatives) {
        // Сырьё предпочтительнее прямой руды: raw_iron добывается из любой железной
        // руды (iron_ore и deepslate_iron_ore — оба в DropTable), а deepslate_iron_ore
        // — частный блок, которого в мире может не быть: плавка через него план
        // строит добычу конкретного блока вместо универсального сырья.
        Optional<String> raw = pickObtainable(alternatives.stream()
                .filter(alternative -> ownBlock(alternative).isEmpty()));
        if (raw.isPresent()) {
            return raw.get();
        }
        return pickObtainable(alternatives.stream())
                .orElseGet(() -> alternatives.stream().sorted().findFirst().orElseThrow());
    }

    private Optional<String> pickObtainable(Stream<String> candidates) {
        return candidates
                .filter(alternative -> canObtain(alternative, new HashSet<>()))
                .findFirst();
    }

    private boolean canObtain(String item, Set<String> path) {
        if (!path.add(item)) {
            return false;
        }
        try {
            if (isGatherable(item)) {
                return true;
            }
            Optional<RecipeSpec> craft = craftRecipe(item, Map.of());
            if (craft.isPresent()) {
                return craft.get().ingredients().stream()
                        .allMatch(slot -> slot.alternatives().stream().anyMatch(alt -> canObtain(alt, path)));
            }
            return smeltRecipes(item).stream()
                    .anyMatch(recipe ->
                            recipe.ingredients().get(0).alternatives().stream().anyMatch(alt -> canObtain(alt, path)));
        } finally {
            path.remove(item);
        }
    }

    private static RecipeIndex buildIndex(RecipeManager manager, HolderLookup.Provider registries) {
        INDEX_BUILDS.incrementAndGet();
        return RecipeIndex.of(RecipeCurator.curate(RecipeSource.read(manager, registries)));
    }

    private record Cached(RecipeManager manager, RecipeIndex index) {}
}

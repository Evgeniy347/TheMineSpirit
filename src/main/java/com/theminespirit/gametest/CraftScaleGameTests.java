package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.FIELD;
import static com.theminespirit.gametest.CraftTestSupport.TIMEOUT;
import static com.theminespirit.gametest.CraftTestSupport.assertFinished;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.launch;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.CraftTestSupport.stack;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.FuelLoad;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.FurnaceSession;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.crafting.RecipeSource;
import com.theminespirit.neoforge.crafting.StationFinder;
import com.theminespirit.neoforge.crafting.StationType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftScaleGameTests {
    private static final List<String> QUERIED_ITEMS =
            List.of("stick", "oak_planks", "iron_ingot", "wooden_pickaxe", "furnace", "cooked_beef", "bread", "torch");
    private static final int REPEATS = 25;
    private static final int QUERY_KINDS = 4;
    private static final int MAX_PROBES_PER_QUERY = 2;
    private static final int MAX_BUILDS_PER_MANAGER = 1;
    private static final int GRID_SLOTS = 9;
    private static final int MAX_SLICES_PER_TICK = 4;
    private static final int OBSERVED_TICKS = 100;
    private static final int MAX_POLLS_IN_OBSERVED_TICKS = 10;
    private static final BlockPos FURNACE_CELL = new BlockPos(3, 1, 1);
    private static final BlockPos TABLE_CELL = new BlockPos(3, 1, 1);

    private CraftScaleGameTests() {}

    @GameTest(template = FIELD, batch = "craft_scale_index", timeoutTicks = TIMEOUT)
    public static void index_ManyKnowledgeCalls_BuildsOncePerManagerAndLooksUpInConstantProbes(GameTestHelper helper) {
        RecipeManager real = helper.getLevel().getRecipeManager();
        RecipeManager fresh = new RecipeManager(helper.getLevel().registryAccess());
        fresh.replaceRecipes(real.getRecipes());
        int buildsBefore = RecipeKnowledge.indexBuilds();

        RecipeKnowledge first = RecipeKnowledge.of(fresh, helper.getLevel().registryAccess());
        int probesBefore = first.index().lookupProbes();
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            RecipeKnowledge knowledge =
                    RecipeKnowledge.of(fresh, helper.getLevel().registryAccess());
            for (String item : QUERIED_ITEMS) {
                knowledge.isCraftable(item);
                knowledge.outputCount(item);
                knowledge.stationFor(item);
                knowledge.isSmeltable(item);
            }
        }

        int builds = RecipeKnowledge.indexBuilds() - buildsBefore;
        helper.assertTrue(
                builds <= MAX_BUILDS_PER_MANAGER,
                "индекс рецептов обязан строиться один раз на менеджер, построений за " + REPEATS * QUERIED_ITEMS.size()
                        + " обращений: " + builds);
        int probes = first.index().lookupProbes() - probesBefore;
        int queries = REPEATS * QUERIED_ITEMS.size() * QUERY_KINDS;
        helper.assertTrue(
                probes <= queries * MAX_PROBES_PER_QUERY,
                "поиск рецепта обязан быть O(1): не больше " + MAX_PROBES_PER_QUERY + " обращений к карте на запрос, "
                        + "фактически " + probes + " на " + queries + " запросов");
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "craft_scale_grid", timeoutTicks = TIMEOUT)
    public static void recipes_AllServerRecipes_FitGridOfNineSlots(GameTestHelper helper) {
        List<RecipeSpec> specs = RecipeSource.read(
                helper.getLevel().getRecipeManager(), helper.getLevel().registryAccess());

        int widest = specs.stream()
                .mapToInt(recipe -> recipe.ingredients().size())
                .max()
                .orElse(0);

        helper.assertTrue(
                !specs.isEmpty() && widest <= GRID_SLOTS,
                "ни один рецепт не вправе требовать больше " + GRID_SLOTS + " мест сетки за крафт, самый большой: "
                        + widest + " из " + specs.size() + " рецептов");
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = "craft_scale_search", timeoutTicks = TIMEOUT)
    public static void finder_RadiusThirtyTwo_UsesAtMostFourSlicesPerTick(GameTestHelper helper) {
        field(helper);
        StationFinder finder = new StationFinder(
                helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 5)), 32, StationType.CRAFTING_TABLE);

        helper.succeedWhen(() -> {
            helper.assertTrue(finder.step(), "поиск станции ещё идёт");
            helper.assertTrue(
                    finder.maxSlicesPerTick() <= MAX_SLICES_PER_TICK,
                    "поиск станции обязан брать не больше " + MAX_SLICES_PER_TICK + " срезов за тик, взял "
                            + finder.maxSlicesPerTick());
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = FIELD, batch = "craft_scale_poll", timeoutTicks = TIMEOUT)
    public static void furnaceSession_ObservedHundredTicks_PollsAtMostOncePerTenTicks(GameTestHelper helper) {
        field(helper);
        helper.setBlock(FURNACE_CELL, Blocks.FURNACE);
        SpiritEntity spirit = prepare(helper, stack(Items.RAW_IRON, 3), stack(Items.COAL, 2));
        FurnaceSession.Job job =
                new FurnaceSession.Job(Items.RAW_IRON, 3, 1, List.of(new FuelLoad.Portion("coal", 1)), 200, 3, 0, 3);
        FurnaceSession session = new FurnaceSession(spirit, helper.absolutePos(FURNACE_CELL), job);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (ticks[0] < OBSERVED_TICKS) {
                ticks[0]++;
                session.tick();
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(ticks[0] >= OBSERVED_TICKS, "наблюдение ещё идёт: тиков " + ticks[0]);
            helper.assertTrue(
                    session.polls() <= MAX_POLLS_IN_OBSERVED_TICKS,
                    "печь обязана опрашиваться не чаще раза в 10 тиков: за " + OBSERVED_TICKS + " тиков опросов "
                            + session.polls() + " вместо не более " + MAX_POLLS_IN_OBSERVED_TICKS);
            session.cancel();
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_scale_no_search", timeoutTicks = TIMEOUT)
    public static void craft_RecipeWithoutStation_StartsNoWorldSearch(GameTestHelper helper) {
        field(helper);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 2));
        int before = StationFinder.created();
        SpiritRun run = launch(helper, spirit, "craft", "stick", 4);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            int searches = StationFinder.created() - before;
            helper.assertTrue(
                    searches == 0, "крафт 2×2 не вправе обходить мир в поисках станции, поисков начато " + searches);
            cleanup(helper, spirit);
        });
    }

    @GameTest(template = FIELD, batch = "craft_scale_one_search", timeoutTicks = TIMEOUT)
    public static void craft_RecipeWithTable_StartsExactlyOneWorldSearch(GameTestHelper helper) {
        field(helper);
        helper.setBlock(TABLE_CELL, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper, stack(Items.OAK_PLANKS, 6), stack(Items.STICK, 4));
        int before = StationFinder.created();
        SpiritRun run = launch(helper, spirit, "craft", "wooden_pickaxe", 2);

        helper.succeedWhen(() -> {
            assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            int searches = StationFinder.created() - before;
            helper.assertTrue(
                    searches == 1,
                    "поиск верстака обязан начаться один раз за скрипт (на два крафта), начато " + searches);
            cleanup(helper, spirit);
        });
    }
}

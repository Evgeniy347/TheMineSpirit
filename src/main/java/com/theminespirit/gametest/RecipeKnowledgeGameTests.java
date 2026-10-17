package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.crafting.IngredientSpec;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceResolver;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeKnowledgeGameTests {
    private static final String FIELD = "arena";
    private static final String BATCH = "recipe_knowledge";
    private static final int TIMEOUT = 20;

    private RecipeKnowledgeGameTests() {
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void stick_ServerRecipes_CraftableFourPerCraftFromTwoPlanks(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(knowledge.isCraftable("stick"),
                "палка обязана быть craftable по рецептам сервера: без этого резолвер не спланирует крафт палок");
        helper.assertTrue(knowledge.outputCount("stick") == 4,
                "рецепт палок даёт 4 за крафт, знания вернули " + knowledge.outputCount("stick"));
        List<Ingredient> ingredients = knowledge.ingredientsFor("stick");
        helper.assertTrue(ingredients.size() == 1 && ingredients.get(0).count() == 2
                && ingredients.get(0).item().endsWith("_planks"),
                "палка обязана требовать 2 доски одним ингредиентом, знания вернули " + ingredients);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void woodenPickaxe_ServerRecipes_NeedsCraftingTableAndThreePlanksTwoSticks(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(knowledge.stationFor("wooden_pickaxe").equals(Optional.of("crafting_table")),
                "кирка 3×3 обязана требовать верстак, знания вернули " + knowledge.stationFor("wooden_pickaxe"));
        helper.assertTrue(knowledge.stationFor("stick").isEmpty(),
                "палка идёт в сетке 2×2 инвентаря: станции быть не должно, знания вернули "
                        + knowledge.stationFor("stick"));
        List<Ingredient> ingredients = knowledge.ingredientsFor("wooden_pickaxe");
        helper.assertTrue(ingredients.size() == 2 && ingredients.get(0).count() == 3
                && ingredients.get(0).item().endsWith("_planks") && ingredients.get(1).equals(new Ingredient("stick", 2)),
                "кирка обязана требовать 3 доски и 2 палки, знания вернули " + ingredients);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void ironIngot_ServerRecipes_SmeltedNotCraftedFromNuggets(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(knowledge.isSmeltable("iron_ingot"), "слиток железа обязан быть smeltable");
        helper.assertTrue(!knowledge.isCraftable("iron_ingot"),
                "слиток железа не должен считаться craftable (9 самородков или блок): резолвер уйдёт в цикл "
                        + "слиток → самородок → слиток вместо плавки");
        String input = knowledge.smeltInputFor("iron_ingot");
        boolean isRecipeInput = knowledge.smeltRecipes("iron_ingot").stream().map(recipe -> recipe.ingredients().get(0))
                .map(IngredientSpec::alternatives).anyMatch(alternatives -> alternatives.contains(input));
        helper.assertTrue(isRecipeInput,
                "вход плавки обязан быть входом одного из рецептов плавки слитка, знания вернули " + input);
        helper.assertTrue(knowledge.stationFor("iron_ingot").equals(Optional.of("furnace")),
                "плавка обязана требовать печь, знания вернули " + knowledge.stationFor("iron_ingot"));
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void burnTicks_ServerFuel_TakenFromGame(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(knowledge.burnTicks("coal") == 1600,
                "уголь горит 1600 тиков, знания вернули " + knowledge.burnTicks("coal"));
        helper.assertTrue(knowledge.burnTicks("minecraft:oak_planks") == 300,
                "доски горят 300 тиков по данным игры, знания вернули " + knowledge.burnTicks("minecraft:oak_planks"));
        helper.assertTrue(knowledge.burnTicks("iron_ore") == 0,
                "руда не топливо, знания вернули " + knowledge.burnTicks("iron_ore"));
        helper.assertTrue(knowledge.burnTicks("blaze_rod") == 2400,
                "стержень всполоха горит 2400 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("blaze_rod"));
        helper.assertTrue(knowledge.burnTicks("coal_block") == 16000,
                "блок угля горит 16000 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("coal_block"));
        helper.assertTrue(knowledge.burnTicks("lava_bucket") == 20000,
                "ведро лавы горит 20000 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("lava_bucket"));
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void writtenBook_DynamicRecipe_IsNotCraftable(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(!knowledge.isCraftable("written_book"),
                "книга-копия делается динамическим рецептом (CustomRecipe): модель крафта духа его не поддерживает, "
                        + "иначе дух попробует скрафтить то, что рецептом сетки не описывается");
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void craftRecipe_EmptyInventory_CheapestPlanksForSticks(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        Optional<RecipeSpec> recipe = knowledge.craftRecipe("stick", Map.of());

        helper.assertTrue(recipe.isPresent() && recipe.get().outputCount() == 4,
                "палки при пустом инвентаре обязаны выбирать рецепт из досок (4 за крафт), а не из бамбука (1 за "
                        + "крафт), выбран " + recipe);
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rawIron_DropKnowledge_GatherableFromIronOre(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(knowledge.isGatherable("raw_iron"),
                "raw_iron обязан стать gatherable через знание «дроп ← руда» из drops.json: без него "
                        + "acquire iron_ingot с нуля падает с «не найден способ добычи»");
        helper.assertTrue(knowledge.gatherBlockFor("raw_iron").equals("iron_ore"),
                "gather для raw_iron обязан ломать железную руду, знания вернули "
                        + knowledge.gatherBlockFor("raw_iron"));
        helper.assertTrue(knowledge.isGatherable("oak_log")
                        && knowledge.gatherBlockFor("oak_log").equals("oak_log"),
                "блоки-предметы не должны пострадать от таблицы дропов: oak_log добывается как сам блок, "
                        + "gatherBlockFor вернул " + knowledge.gatherBlockFor("oak_log"));
        helper.succeed();
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void resolve_RawIron_GatherPlanBreaksIronOreBlocks(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        TaskNode plan = ResourceResolver.resolve("raw_iron", 2, knowledge);
        List<String> steps = FullCycleScene.scriptsOf(plan);

        helper.assertTrue(steps.get(steps.size() - 1).equals("gather:iron_ore"),
                "план для сырого железа обязан закончиться сбором железной руды (один блок руды даёт один дроп), "
                        + "резолвер и PlanValidator не должны его отклонить, шаги: " + steps);
        helper.assertTrue(steps.contains("craft:wooden_pickaxe") && steps.contains("craft:stone_pickaxe"),
                "перед рудой план обязан изготовить кирки по уровню инструмента: деревянную для камня и каменную "
                        + "для железной руды; без них gather простоит без прогресса, шаги: " + steps);
        helper.succeed();
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.crafting.IngredientSpec;
import com.theminespirit.core.crafting.RecipeSpec;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceResolver;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTestHelper;

public final class RecipeKnowledgeGameTests {
    private RecipeKnowledgeGameTests() {}

    static void check(GameTestHelper helper) {
        RecipeKnowledge knowledge = RecipeKnowledge.of(helper.getLevel());

        helper.assertTrue(
                knowledge.isCraftable("stick"),
                "палка обязана быть craftable по рецептам сервера: без этого резолвер не спланирует крафт палок");
        helper.assertTrue(
                knowledge.outputCount("stick") == 4,
                "рецепт палок даёт 4 за крафт, знания вернули " + knowledge.outputCount("stick"));
        List<Ingredient> ingredients = knowledge.ingredientsFor("stick");
        helper.assertTrue(
                ingredients.size() == 1
                        && ingredients.get(0).count() == 2
                        && ingredients.get(0).item().endsWith("_planks"),
                "палка обязана требовать 2 доски одним ингредиентом, знания вернули " + ingredients);
        helper.assertTrue(
                "shears".equals(knowledge.toolForBlock("oak_leaves").orElse(null)),
                "Листва добывается ножницами: gather обязан сначала взять или скрафтить ножницы, иначе листья "
                        + "не выпадут; знания вернули " + knowledge.toolForBlock("oak_leaves"));
        helper.assertTrue(
                knowledge.stationFor("wooden_pickaxe").equals(Optional.of("crafting_table")),
                "кирка 3×3 обязана требовать верстак, знания вернули " + knowledge.stationFor("wooden_pickaxe"));
        helper.assertTrue(
                knowledge.stationFor("stick").isEmpty(),
                "палка идёт в сетке 2×2 инвентаря: станции быть не должно, знания вернули "
                        + knowledge.stationFor("stick"));
        List<Ingredient> pickaxe = knowledge.ingredientsFor("wooden_pickaxe");
        helper.assertTrue(
                pickaxe.size() == 2
                        && pickaxe.get(0).count() == 3
                        && pickaxe.get(0).item().endsWith("_planks")
                        && pickaxe.get(1).equals(new Ingredient("stick", 2)),
                "кирка обязана требовать 3 доски и 2 палки, знания вернули " + pickaxe);
        helper.assertTrue(knowledge.isSmeltable("iron_ingot"), "слиток железа обязан быть smeltable");
        helper.assertTrue(
                !knowledge.isCraftable("iron_ingot"),
                "слиток железа не должен считаться craftable (9 самородков или блок): резолвер уйдёт в цикл "
                        + "слиток → самородок → слиток вместо плавки");
        String input = knowledge.smeltInputFor("iron_ingot");
        boolean isRecipeInput = knowledge.smeltRecipes("iron_ingot").stream()
                .map(recipe -> recipe.ingredients().get(0))
                .map(IngredientSpec::alternatives)
                .anyMatch(alternatives -> alternatives.contains(input));
        helper.assertTrue(
                isRecipeInput,
                "вход плавки обязан быть входом одного из рецептов плавки слитка, знания вернули " + input);
        helper.assertTrue(
                knowledge.stationFor("iron_ingot").equals(Optional.of("furnace")),
                "плавка обязана требовать печь, знания вернули " + knowledge.stationFor("iron_ingot"));
        helper.assertTrue(
                knowledge.burnTicks("coal") == 1600,
                "уголь горит 1600 тиков, знания вернули " + knowledge.burnTicks("coal"));
        helper.assertTrue(
                knowledge.burnTicks("minecraft:oak_planks") == 300,
                "доски горят 300 тиков по данным игры, знания вернули " + knowledge.burnTicks("minecraft:oak_planks"));
        helper.assertTrue(
                knowledge.burnTicks("iron_ore") == 0,
                "руда не топливо, знания вернули " + knowledge.burnTicks("iron_ore"));
        helper.assertTrue(
                knowledge.burnTicks("blaze_rod") == 2400,
                "стержень всполоха горит 2400 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("blaze_rod"));
        helper.assertTrue(
                knowledge.burnTicks("coal_block") == 16000,
                "блок угля горит 16000 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("coal_block"));
        helper.assertTrue(
                knowledge.burnTicks("lava_bucket") == 20000,
                "ведро лавы горит 20000 тиков по data map furnace_fuels, знания вернули "
                        + knowledge.burnTicks("lava_bucket"));
        helper.assertTrue(
                !knowledge.isCraftable("written_book"),
                "книга-копия делается динамическим рецептом (CustomRecipe): модель крафта духа его не поддерживает, "
                        + "иначе дух попробует скрафтить то, что рецептом сетки не описывается");
        Optional<RecipeSpec> recipe = knowledge.craftRecipe("stick", Map.of());
        helper.assertTrue(
                recipe.isPresent() && recipe.get().outputCount() == 4,
                "палки при пустом инвентаре обязаны выбирать рецепт из досок (4 за крафт), а не из бамбука (1 за "
                        + "крафт), выбран " + recipe);
        helper.assertTrue(
                knowledge.isGatherable("raw_iron"),
                "raw_iron обязан стать gatherable через знание «дроп ← руда» из drops.json: без него "
                        + "acquire iron_ingot с нуля падает с «не найден способ добычи»");
        helper.assertTrue(
                "iron_ore".equals(knowledge.gatherBlockFor("raw_iron")),
                "gather для raw_iron обязан ломать железную руду, знания вернули "
                        + knowledge.gatherBlockFor("raw_iron"));
        helper.assertTrue(
                knowledge.isGatherable("oak_log") && "oak_log".equals(knowledge.gatherBlockFor("oak_log")),
                "блоки-предметы не должны пострадать от таблицы дропов: oak_log добывается как сам блок, "
                        + "gatherBlockFor вернул " + knowledge.gatherBlockFor("oak_log"));
        TaskNode plan = ResourceResolver.resolve(
                "raw_iron", 2, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        List<String> steps = FullCycleScene.scriptsOf(plan);
        helper.assertTrue(
                "gather:iron_ore".equals(steps.get(steps.size() - 1)),
                "план для сырого железа обязан закончиться сбором железной руды (один блок руды даёт один дроп), "
                        + "резолвер и PlanValidator не должны его отклонить, шаги: " + steps);
        helper.assertTrue(
                steps.contains("craft:wooden_pickaxe") && steps.contains("craft:stone_pickaxe"),
                "перед рудой план обязан изготовить кирки по уровню инструмента: деревянную для камня и каменную "
                        + "для железной руды; без них gather простоит без прогресса, шаги: " + steps);
    }
}

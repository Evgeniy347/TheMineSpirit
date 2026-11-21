package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandFoodChainScenarioPart2Tests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    private static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    private static GraphWorld pair(GraphWorld world) {
        world.tools("spirit-2", 1);
        return world;
    }

    @Test
    void case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt() {
        GraphWorld world = pair(stageOne()).built("furnace", Built.PLANNED);
        world.with("cobblestone", 0).with("stone", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task furnace = task(plan, "building:furnace");
        GraphPlan.Task cobble = task(plan, "mine_cobble");
        assertTrue(
                cobble.feasible(),
                "Булыжника нет — шахта камня поднята:  Иначе тест case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt перестанет ловить эту поломку. При смене case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt проверь это утверждение."
                        + ids(plan));
        assertFalse(
                furnace.feasible(),
                "Без камня печь ждёт материал:  Иначе тест case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt перестанет ловить эту поломку. При смене case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt проверь это утверждение."
                        + ids(plan));
        world.with("cobblestone", 80);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "building:furnace").feasible(),
                "Камень есть — печь становится выполнимой:  Иначе тест case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt перестанет ловить эту поломку. При смене case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("mine_cobble").isEmpty(),
                "Камня хватает — добыча камня снята:  Иначе тест case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt перестанет ловить эту поломку. При смене case05_FurnaceMissing_CobblestoneRaisedUntilFurnaceCanBeBuilt проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case11_NoSheep_BreedingRaisedThenShearing() {
        GraphWorld world = pair(stageTwo()).built("pen_sheep", Built.BUILT);
        world.with("herd_sheep", 0).with("wheat", 4).with("white_wool", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task capture = task(plan, "capture_sheep");
        assertTrue(
                capture.feasible(),
                "Овец нет — граф поднимает поимку/разведение:  Иначе тест case11_NoSheep_BreedingRaisedThenShearing перестанет ловить эту поломку. При смене case11_NoSheep_BreedingRaisedThenShearing проверь это утверждение."
                        + ids(plan));
        world.with("herd_sheep", 2);
        GraphPlan grown = plan(world, facts(TWO));
        GraphPlan.Task shear = task(grown, "shear");
        assertTrue(
                shear.feasible(),
                "Овцы есть — стрижка становится работой:  Иначе тест case11_NoSheep_BreedingRaisedThenShearing перестанет ловить эту поломку. При смене case11_NoSheep_BreedingRaisedThenShearing проверь это утверждение."
                        + ids(grown));
        assertTrue(
                task(grown, "breed_sheep").raised(),
                "Две овцы — разведение продолжается, шерсть не ждёт:  Иначе тест case11_NoSheep_BreedingRaisedThenShearing перестанет ловить эту поломку. При смене case11_NoSheep_BreedingRaisedThenShearing проверь это утверждение."
                        + ids(grown));
        world.with("white_wool", 64);
        GraphPlan full = plan(world, facts(TWO));
        assertTrue(
                full.task("shear").isEmpty(),
                "Шерсти хватает — стрижка не заказывается:  Иначе тест case11_NoSheep_BreedingRaisedThenShearing перестанет ловить эту поломку. При смене case11_NoSheep_BreedingRaisedThenShearing проверь это утверждение."
                        + ids(full));
    }

    @Test
    void case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine() {
        GraphWorld world = pair(stageOne()).event("hungry").event("life_injury");
        world.with("food", 0);
        GraphPlan plan = plan(world, facts(named(spirit("spirit-1", 4, 4), spirit("spirit-2"))));
        GraphPlan.Task heal = task(plan, "event:life_injury:heal");
        GraphPlan.Task hunt = task(plan, "event:hungry:hunt");
        GraphPlan.Task mine = task(plan, "mine_coal");
        assertTrue(
                heal.feasible(),
                "Раненый дух получает лечение:  Иначе тест case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine перестанет ловить эту поломку. При смене case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine проверь это утверждение."
                        + ids(plan));
        assertTrue(
                hunt.feasible(),
                "Еды нет — охота выполнима:  Иначе тест case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine перестанет ловить эту поломку. При смене case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine проверь это утверждение."
                        + ids(plan));
        assertTrue(
                hunt.priority() > mine.priority(),
                "Голодный раненый дух сначала добывает еду, а не копает: охота " + hunt.priority()
                        + ", шахта  Иначе тест case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine перестанет ловить эту поломку. При смене case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine проверь это утверждение."
                        + mine.priority());
        assertTrue(
                heal.priority() > mine.priority(),
                "Лечение выше обычной шахты: лечение " + heal.priority()
                        + ", шахта  Иначе тест case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine перестанет ловить эту поломку. При смене case12_LowHealthWithoutFood_FoodAndHealingRaisedAboveMine проверь это утверждение."
                        + mine.priority());
    }

    @Test
    void case19_FieldDry_WaterBucketRaisedThroughBucketAndIron() {
        GraphWorld world = pair(stageTwo()).event("more5_crops_dry");
        world.with("water_bucket", 0).with("bucket", 0).with("iron_ingot", 0).with("raw_iron", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task fill = task(plan, "fill_water_bucket");
        GraphPlan.Task bucket = task(plan, "craft_bucket");
        assertTrue(
                fill.raised() || bucket.raised(),
                "Воды нет — граф поднимает ведро и его железо:  Иначе тест case19_FieldDry_WaterBucketRaisedThroughBucketAndIron перестанет ловить эту поломку. При смене case19_FieldDry_WaterBucketRaisedThroughBucketAndIron проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "event:more5_crops_dry:more5_water_crops").feasible(),
                "Полив остаётся работой события:  Иначе тест case19_FieldDry_WaterBucketRaisedThroughBucketAndIron перестанет ловить эту поломку. При смене case19_FieldDry_WaterBucketRaisedThroughBucketAndIron проверь это утверждение."
                        + ids(plan));
        world.with("water_bucket", 8).with("bucket", 4).with("iron_ingot", 8);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("fill_water_bucket").isEmpty(),
                "Вёдер воды хватает — набрать ещё не надо:  Иначе тест case19_FieldDry_WaterBucketRaisedThroughBucketAndIron перестанет ловить эту поломку. При смене case19_FieldDry_WaterBucketRaisedThroughBucketAndIron проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_bucket").isEmpty(),
                "Вёдра есть — крафт ведра снят:  Иначе тест case19_FieldDry_WaterBucketRaisedThroughBucketAndIron перестанет ловить эту поломку. При смене case19_FieldDry_WaterBucketRaisedThroughBucketAndIron проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case20_MiningKit_FoodTorchesPickaxeRaised() {
        GraphWorld world = pair(stageTwo()).event("gear_kit_mining").tools("spirit-1", -1);
        world.with("food", 0)
                .with("torch", 0)
                .with("coal", 0)
                .with("cobblestone", 40)
                .with("stick", 10);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task pick = task(plan, "craft_stone_pickaxe@spirit-1");
        GraphPlan.Task torch = task(plan, "craft_torch");
        GraphPlan.Task food = task(plan, "craft_food");
        assertTrue(
                pick.feasible(),
                "Кирки нет — набор шахты поднимает крафт кирки:  Иначе тест case20_MiningKit_FoodTorchesPickaxeRaised перестанет ловить эту поломку. При смене case20_MiningKit_FoodTorchesPickaxeRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                torch.raised(),
                "Факелов нет — набор шахты поднимает факелы:  Иначе тест case20_MiningKit_FoodTorchesPickaxeRaised перестанет ловить эту поломку. При смене case20_MiningKit_FoodTorchesPickaxeRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                food.raised(),
                "Еды нет — шахтёр получает и еду:  Иначе тест case20_MiningKit_FoodTorchesPickaxeRaised перестанет ловить эту поломку. При смене case20_MiningKit_FoodTorchesPickaxeRaised проверь это утверждение."
                        + ids(plan));
    }

    @Test
    void case24_NoBowOrArrows_GuardCraftsBowAndArrows() {
        GraphWorld world = pair(stageTwo()).event("hostile_nearby");
        world.with("bow", 0).with("arrow", 0).with("string", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphWorld calm = world.copy();
        calm.events.remove("hostile_nearby");
        GraphPlan without = plan(calm, facts(TWO));
        GraphPlan.Task bow = task(plan, "craft_bow");
        GraphPlan.Task arrows = task(plan, "craft_arrow");
        assertTrue(
                bow.priority() > without.priority("craft_bow"),
                "Угроза поднимает лук выше обычного: с угрозой " + bow.priority() + ", без "
                        + without.priority(
                                "craft_bow Иначе тест case24_NoBowOrArrows_GuardCraftsBowAndArrows перестанет ловить эту поломку. При смене case24_NoBowOrArrows_GuardCraftsBowAndArrows проверь это утверждение."));
        assertTrue(
                arrows.priority() > without.priority("craft_arrow"),
                "Угроза поднимает стрелы: с угрозой " + arrows.priority() + ", без "
                        + without.priority(
                                "craft_arrow Иначе тест case24_NoBowOrArrows_GuardCraftsBowAndArrows перестанет ловить эту поломку. При смене case24_NoBowOrArrows_GuardCraftsBowAndArrows проверь это утверждение."));
        world.with("bow", 4).with("arrow", 256).with("string", 64);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("craft_bow").isEmpty(),
                "Лук есть — крафт лука снят:  Иначе тест case24_NoBowOrArrows_GuardCraftsBowAndArrows перестанет ловить эту поломку. При смене case24_NoBowOrArrows_GuardCraftsBowAndArrows проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_arrow").isEmpty(),
                "Стрел хватает — крафт стрел снят:  Иначе тест case24_NoBowOrArrows_GuardCraftsBowAndArrows перестанет ловить эту поломку. При смене case24_NoBowOrArrows_GuardCraftsBowAndArrows проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case28_SugarCaneMissing_PlantingRaisedUntilStocked() {
        GraphWorld world = pair(stageTwo()).with("sugar_cane", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task forage = task(plan, "forage_cane");
        assertTrue(
                forage.feasible(),
                "Тростника нет — граф поднимает его сбор/посадку:  Иначе тест case28_SugarCaneMissing_PlantingRaisedUntilStocked перестанет ловить эту поломку. При смене case28_SugarCaneMissing_PlantingRaisedUntilStocked проверь это утверждение."
                        + ids(plan));
        world.with("sugar_cane", 32);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("forage_cane").isEmpty(),
                "Тростника хватает — сбор снят:  Иначе тест case28_SugarCaneMissing_PlantingRaisedUntilStocked перестанет ловить эту поломку. При смене case28_SugarCaneMissing_PlantingRaisedUntilStocked проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("harvest_cane").isEmpty(),
                "Тростника хватает — посадка снята:  Иначе тест case28_SugarCaneMissing_PlantingRaisedUntilStocked перестанет ловить эту поломку. При смене case28_SugarCaneMissing_PlantingRaisedUntilStocked проверь это утверждение."
                        + ids(stocked));
    }
}

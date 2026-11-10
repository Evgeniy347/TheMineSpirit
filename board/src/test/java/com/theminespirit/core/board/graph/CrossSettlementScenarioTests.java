package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossActions.supplyTarget;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static com.theminespirit.core.board.graph.CrossScene.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossSettlementScenarioTests {
    @Test
    void fill_PitOnHouseLot_FillAndBuildStayPostedTogether() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64).with("dirt", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task backfill = task(plan, "event:backfill:backfill");
        GraphPlan.Task house = task(plan, "building:hut");

        assertTrue(backfill.feasible(), "Яма на участке дома засыпается: " + ids(plan));
        assertTrue(house.feasible(), "Стройка дома не исчезает из-за ямы на участке: " + ids(plan));
        assertTrue(
                scripts(BoardCase.FILL_CRATER).contains("fill"),
                "Засыпка выполняется скриптом fill, а не выравниванием руками: " + scripts(BoardCase.FILL_CRATER));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Дом строится схемой build_schematic: " + scripts(BoardCase.BUILD_HOUSE));
        assertTrue(
                plan.tasks().stream().noneMatch(task -> "dirt".equals(task.makes()) && task.feasible()),
                "Земли в сундуках хватает — граф не ставит новую добычу земли: " + ids(plan));

        GraphPlan flat = plan(
                world,
                facts(named(spirit("spirit-1")), List.of(), List.of(), GraphSiteFacts.Terrain.empty(), Map.of()));
        assertTrue(
                flat.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "После засыпки дом остаётся выполнимой работой: " + ids(flat));
    }

    @Test
    void demolish_NonEmptyStrayChest_DrainsToWarehouseBeforeBreaking() {
        GraphWorld world = stageOne();
        GraphFacts facts = new GraphFacts(
                named(spirit("spirit-1")),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(new GraphSiteFacts.Stray(
                        "stray:chest@1,64,1",
                        "chest",
                        new GraphFacts.Position(1, 64, 1),
                        "spirit-1",
                        Map.of("minecraft:chest", 1))),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task demolition = task(plan, "event:demolish_stray:demolish_stray");
        assertTrue(demolition.feasible(), "Лишний сундук получает задание сноса: " + ids(plan));

        List<Stack> slots = new ArrayList<>(List.of(new Stack("minecraft:bread", 0, 32, 64)));
        ContainerSnapshot chest = new ContainerSnapshot(new BlockCoord(1, 64, 1), slots);
        List<ScriptCall> calls = new ArrayList<>();
        collect(ChestPlans.demolish(chest, List.of(new BlockCoord(1, 64, 1))), calls);
        int withdraw = index(calls, "withdraw");
        int deposit = index(calls, "deposit");
        int clear = index(calls, "clear_area");

        assertTrue(withdraw >= 0, "Сундук сначала вынимают скриптом withdraw: " + scriptsOf(calls));
        assertTrue(deposit > withdraw, "Вынутое относят на склад до сноса: " + scriptsOf(calls));
        assertTrue(clear > deposit, "Ломают сундук только после выноса: " + scriptsOf(calls));
        assertEquals(
                "minecraft:bread",
                calls.get(withdraw).params().get("item"),
                "Из сундука вынимают именно его содержимое, а не первый попавшийся предмет: " + calls);
        assertTrue(
                BoardActivities.caseOfScript("clear_area")
                        .filter(BoardCase.ACTIVITY_BUILD::equals)
                        .isPresent(),
                "Снос идёт действием разбора, а не новой механикой");
    }

    @Test
    void husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing() {
        GraphWorld world = stageTwo();
        world.with("wheat", 0)
                .with("wheat_seeds", 0)
                .with("carrot", 0)
                .with("potato", 0)
                .with("beetroot", 0);
        world.event("animal_starving").event("field_empty").event("more2_food_low");
        GraphFacts facts = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task seeds = task(plan, "gather_seeds");
        GraphPlan.Task feed = task(plan, "event:animal_starving:feed_animals");
        GraphPlan.Task plant = task(plan, "event:field_empty:plant_crop");

        assertTrue(seeds.feasible(), "Без урожая граф идёт за семенами, а не кормит скот воздухом: " + ids(plan));
        assertFalse(feed.feasible(), "Кормить животных нечем: feed_animals ждёт урожай: " + ids(plan));
        assertFalse(plant.feasible(), "Сеять нечем: plant_crop ждёт семена: " + ids(plan));
        assertTrue(seeds.priority() > feed.priority(), "Семена важнее невыполнимого кормления: " + ids(plan));
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, supplyTarget("wheat_seeds")).stream()
                        .anyMatch(script ->
                                Set.of("gather", "acquire", "harvest_crop").contains(script)),
                "Семена добываются сбором, а не крафтом из воздуха: "
                        + scripts(BoardCase.GATHER_MATERIALS, supplyTarget("wheat_seeds")));
        assertEquals(
                BoardCase.ACTIVITY_FORAGE,
                BoardActivities.caseOfScript("gather").orElseThrow(),
                "Сбор семян — действие заготовки, а не животноводства");
    }

    @Test
    void mining_DarkMine_CoalThenTorchThenLightInGraphOrder() {
        GraphWorld world = stageOne();
        world.with("coal", 0).with("torch", 0).with("stick", 64);
        world.event("more5_mine_dark");
        GraphFacts facts = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphPlan first = plan(world, facts);

        GraphPlan.Task coal = task(first, "mine_coal");
        GraphPlan.Task light = task(first, "event:more5_mine_dark:more5_light_mine");
        GraphPlan.Task torch = task(first, "event:more5_mine_dark:more5_craft_mine_torch");

        assertTrue(coal.feasible(), "Тьма в шахте сначала ведёт к углю: " + ids(first));
        assertFalse(light.feasible(), "Свет без факела не ставится: " + ids(first));
        assertFalse(torch.feasible(), "Факел без угля не крафтится: " + ids(first));

        world.with("coal", 8);
        GraphPlan second = plan(world, facts);
        assertTrue(
                task(second, "event:more5_mine_dark:more5_craft_mine_torch").feasible(),
                "Уголь появился — граф ставит крафт факела: " + ids(second));
        assertFalse(
                task(second, "event:more5_mine_dark:more5_light_mine").feasible(),
                "Пока факела нет, свет не ставится: " + ids(second));

        world.with("torch", 8);
        GraphPlan third = plan(world, facts);
        assertTrue(
                task(third, "event:more5_mine_dark:more5_light_mine").feasible(),
                "Факел есть — граф разрешает осветить шахту: " + ids(third));
        assertTrue(
                scripts(BoardCase.CRAFT_TORCHES).contains("craft"),
                "Крафт факелов идёт скриптом craft: " + scripts(BoardCase.CRAFT_TORCHES));
        assertTrue(
                scripts(BoardCase.LIGHTING).contains("place_block"),
                "Свет ставится блоками place_block: " + scripts(BoardCase.LIGHTING));
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, supplyTarget("coal")).stream()
                        .anyMatch(script ->
                                Set.of("mine_vein", "dig_area", "safe_mine").contains(script)),
                "Уголь добывается шахтным действием: " + scripts(BoardCase.GATHER_MATERIALS, supplyTarget("coal")));
    }

    @Test
    void repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool() {
        GraphWorld stageOneWorld = stageOne();
        GraphFacts worn = facts(
                named(spirit("spirit-1", 20, 20, false, false, null, List.of(tool("pickaxe", 1, 1, 100)), List.of())));
        GraphPlan first = plan(stageOneWorld, worn);
        GraphPlan.Task repair = task(first, "event:tool_worn:repair_tool");

        assertFalse(repair.feasible(), "Без наковальни и слитка инструмент не чинится: " + ids(first));
        assertTrue(
                first.task("mine_coal").filter(GraphPlan.Task::feasible).isPresent(),
                "Изношенная кирка не останавливает шахту: " + ids(first));

        GraphWorld stageTwoWorld = stageTwo();
        stageTwoWorld.built("anvil", Built.BUILT).with("iron_ingot", 8);
        GraphPlan second = plan(stageTwoWorld, worn);
        GraphPlan.Task repaired = task(second, "event:tool_worn:repair_tool");

        assertTrue(repaired.feasible(), "Наковальня и слиток есть — граф ставит ремонт кирки: " + ids(second));
        assertTrue(repaired.priority() > 0, "Ремонт инструмента имеет свой вес, а не ноль");
        assertFalse(
                scripts(BoardCase.REPAIR_TOOL).isEmpty(),
                "Ремонт инструмента имеет план действий, а не пустую карточку");
        assertEquals(
                BoardCase.ACTIVITY_CRAFT,
                BoardActivities.caseOfScript("repair_tool").orElseThrow(),
                "Ремонт инструмента — действие крафта, а не боя");
    }

    @Test
    void combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair() {
        GraphWorld world = stageOne();
        world.built("fence", Built.PLANNED);
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task repair = task(plan, "event:repair_fence:repair:fence");

        assertEquals(100, defend.priority(), "Мобы у раненого духа — защита на потолке: " + ids(plan));
        assertTrue(repair.feasible(), "Побитый забор остаётся выполнимым ремонтом: " + ids(plan));
        assertTrue(defend.priority() > repair.priority(), "Сначала бой, потом забор: " + ids(plan));
        assertTrue(
                scripts(BoardCase.BUILD_FENCE).contains("build_schematic"),
                "Забор ставится схемой build_schematic: " + scripts(BoardCase.BUILD_FENCE));
        assertTrue(
                scripts(BoardCase.REPAIR_BUILDING).contains("build_schematic"),
                "Ремонт постройки использует ту же схему, а не отдельный чинт: " + scripts(BoardCase.REPAIR_BUILDING));
    }

    private static int index(List<ScriptCall> calls, String script) {
        for (int position = 0; position < calls.size(); position++) {
            if (script.equals(calls.get(position).script())) {
                return position;
            }
        }
        return -1;
    }

    private static List<String> scriptsOf(List<ScriptCall> calls) {
        return calls.stream().map(ScriptCall::script).toList();
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}

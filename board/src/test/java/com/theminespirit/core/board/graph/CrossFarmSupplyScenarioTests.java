package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossActions.supplyTarget;
import static com.theminespirit.core.board.graph.CrossScene.chest;
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
import static com.theminespirit.core.board.graph.CrossScene.ticketFor;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.DemolitionScope;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.hunger.SpiritHunger;
import com.theminespirit.core.sleep.SleepFacts;
import com.theminespirit.core.sleep.SleepRule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossFarmSupplyScenarioTests {
    @Test
    void husbandry_ShearingAndWornShears_RepairAndFenceStayPosted() {
        GraphWorld world = stageTwo();
        world.built("pen_sheep", Built.BUILT).built("anvil", Built.BUILT);
        world.with("herd_sheep", 6)
                .with("white_wool", 0)
                .with("wheat", 0)
                .with("wheat_seeds", 0)
                .with("iron_ingot", 8);
        world.event("animal_starving");
        GraphFacts facts = new GraphFacts(
                named(
                        spirit("spirit-1", 20, 20, false, false, null, List.of(wornShears()), List.of()),
                        spirit("spirit-2")),
                List.of(threat("wolf-a", "animal-1", GraphFacts.TargetKind.ANIMAL, 2)),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task shear = task(plan, "shear");
        GraphPlan.Task repairTool = task(plan, "event:tool_worn:repair_tool");
        GraphPlan.Task repairFence = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");

        assertTrue(shear.feasible(), "Овцы в овчарне — стрижка выполнима: " + ids(plan));
        assertTrue(repairTool.feasible(), "Наковальня и слиток — изношенные ножницы чинятся: " + ids(plan));
        assertTrue(repairFence.feasible(), "Побитый забор чинится рядом со стрижкой и ремонтом: " + ids(plan));
        assertTrue(defend.feasible(), "Волк у стада поднимает защиту: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_DEFEND,
                defend.boardCase(),
                "Защита стада — бой, а стрижка остаётся работой: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_FARM,
                BoardActivities.caseOfScript("shear").orElseThrow(),
                "Стрижка овец — занятие фермы, а не бой");
        assertTrue(
                scripts(BoardCase.REPAIR_TOOL).contains("repair_tool"),
                "Инструмент чинится действием repair_tool: " + scripts(BoardCase.REPAIR_TOOL));
        assertTrue(
                scripts(BoardCase.SHEAR_SHEEP, supplyTarget("white_wool")).contains("shear"),
                "Стрижка идёт скриптом shear: " + scripts(BoardCase.SHEAR_SHEEP, supplyTarget("white_wool")));
        assertTrue(
                scripts(BoardCase.BUILD_FENCE).contains("build_schematic"),
                "Загон и забор строятся схемой build_schematic: " + scripts(BoardCase.BUILD_FENCE));
    }

    @Test
    void hunger_NoFoodButExpeditionReady_HungrySpiritDoesNotLeave() {
        GraphWorld world = stageTwo();
        world.tools("spirit-2", 1);
        world.with("food", 0).with("wheat", 0).with("oak_log", 0).with("coal", 0);
        world.event("hungry").event("expedition_ready");
        GraphFacts facts = facts(named(spirit("spirit-1", 20, 4), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        GraphPlan.Task craftFood = task(plan, "craft_food");

        assertTrue(expedition.feasible(), "Поход ставится на доску: " + ids(plan));
        assertFalse(eat.feasible(), "Еды нет — поесть нечем: " + ids(plan));
        assertFalse(craftFood.feasible(), "Пшеницы нет — еда не появляется крафтом из воздуха: " + ids(plan));
        assertTrue(
                SpiritHunger.of(4, 0, 0).foodLevel() < HungerRules.defaults().eatFoodThreshold(),
                "Сытость 4 ниже порога обеда: дух голоден");
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                expedition.boardCase(),
                "Поход остаётся отдельным кейсом, а не подменяет еду");

        GraphTickets.Result result = tickets(world, facts);
        DispatchRound round = new DispatchRound(board(world, result), 0);
        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        assertEquals(
                "spirit-2",
                round.choose(boardTicket(ticketFor(result, GraphTickets.id(expedition))), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Голодный дух не уходит в поход, пока нужда жива: поход достаётся сытому, а голодный ждёт еду");
    }

    @Test
    void supply_FullChestFoodIron_FeedRepairAndSpaceStayPosted() {
        GraphWorld world = stageTwo();
        world.storageFree = 2;
        world.built("anvil", Built.BUILT);
        world.with("food", 8).with("wheat", 0).with("wheat_seeds", 0).with("iron_ingot", 8);
        world.event("hungry").event("tool_worn").event("field_empty");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 4), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(chest(Map.of("food", 8, "wheat_seeds", 8, "iron_ingot", 8))),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        GraphPlan.Task repair = task(plan, "event:tool_worn:repair_tool");
        GraphPlan.Task store = task(plan, "store_space");

        assertEquals(100, defend.priority(), "Набег на голодного духа поднимает защиту на потолок: " + ids(plan));
        assertTrue(eat.feasible(), "Еда на складе делает обед выполнимым: " + ids(plan));
        assertTrue(repair.feasible(), "Наковальня и слиток на складе чинят кирку: " + ids(plan));
        assertTrue(store.feasible(), "Переполненный склад ставит задачу места: " + ids(plan));
        assertTrue(defend.priority() > store.priority(), "Сначала бой, потом раскладка по сундукам: " + ids(plan));
        assertTrue(repair.priority() > store.priority(), "Ремонт кирки выше раскладки по сундукам: " + ids(plan));
        assertEquals(BoardCase.EAT_TO_FULL, eat.boardCase(), "Голод закрывается кейсом EAT_TO_FULL");
        assertTrue(
                scripts(BoardCase.STOCK_CHESTS).contains("deposit"),
                "Раскладка идёт скриптом deposit: " + scripts(BoardCase.STOCK_CHESTS));
    }

    @Test
    void night_DangerAndThreat_CraftWaitsBehindRetreat() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0).with("coal", 0);
        world.event("night");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task retreat = task(plan, "event:life_threat:retreat");
        GraphPlan.Task craft = task(plan, "event:gear_craft_pickaxe:craft_tool:pickaxe");

        assertTrue(craft.feasible(), "Ночью граф всё равно заказывает кирку: " + ids(plan));
        assertTrue(retreat.priority() > craft.priority(), "Отход от мобов выше ночного крафта: " + ids(plan));
        assertTrue(
                SleepRule.timeToSleep(new SleepFacts(true, false, true, false)),
                "Без опасности ночью механика сна разрешает спать");
        assertFalse(
                SleepRule.timeToSleep(new SleepFacts(true, true, true, false)),
                "С мобами механика сна спать не даёт, и крафт идёт раньше сна");
        assertEquals(
                BoardCase.ACTIVITY_CRAFT,
                BoardActivities.caseOfScript("craft").orElseThrow(),
                "Кирка делается крафтом, а не добычей");
    }

    @Test
    void audit_DamagedHutExtraStray_RepairAndDemolitionBeforeStageClose() {
        GraphWorld world = stageOne();
        world.built("fence", Built.PLANNED).built("hut", Built.BUILT);
        world.with("oak_fence", 0).with("oak_log", 64).with("oak_planks", 64).with("dirt", 64);
        GraphFacts facts = new GraphFacts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(
                        "fence",
                        damaged("fence", 40, 3, Map.of("oak_fence", 5)),
                        "hut",
                        new GraphFacts.Building("hut", 1, 80, 0, Map.of(), Map.of("dirt", 3), 2, null, false, true)),
                List.of(new GraphSiteFacts.Stray(
                        "stray:oak_fence@1,69,1", "oak_fence", new GraphFacts.Position(1, 69, 1), "spirit-1")),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task repairHut = task(plan, "event:repair_hut:repair:hut");
        GraphPlan.Task repairFence = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task demolish = task(plan, "event:demolish_stray:demolish_stray");

        assertTrue(repairHut.feasible(), "Повреждённая хижина получает ремонт: " + ids(plan));
        assertTrue(repairFence.feasible(), "Побитый забор чинится рядом: " + ids(plan));
        assertTrue(demolish.feasible(), "Лишний забор получает снос: " + ids(plan));
        assertFalse(
                GraphStageGate.buildingReady(facts, "hut"),
                "Хижина на 80 % не готова: аудит не закрывает этап по журналу");
        assertFalse(GraphStageGate.straysReady(facts), "Лишняя постройка не даёт этапу закрыться");
        assertEquals(
                DemolitionScope.STRAYS,
                DemolitionScope.of(ticketFor(tickets(world, facts), GraphTickets.id(demolish))
                        .id()),
                "Снос лишнего убирает только чужие постройки, а не висящее и не план");
        assertEquals(
                BoardCase.DISMANTLE_SHELTER, demolish.boardCase(), "Снос идёт кейсом уборки, а не ремонтом хижины");
        assertTrue(
                scripts(BoardCase.REPAIR_BUILDING).contains("build_schematic"),
                "Ремонт хижины идёт схемой build_schematic: " + scripts(BoardCase.REPAIR_BUILDING));

        GraphFacts repaired = new GraphFacts(
                facts.spirits(),
                facts.threats(),
                facts.chests(),
                facts.resources(),
                facts.stations(),
                Map.of("hut", new GraphFacts.Building("hut", 1, 100, 0, Map.of(), Map.of(), 0, null, false, true)),
                List.of(),
                facts.terrain(),
                facts.stageReqs(),
                facts.feedback(),
                facts.players(),
                facts.planBreaches());
        assertTrue(
                GraphStageGate.buildingReady(repaired, "hut"),
                "После аудита 100 % и снятия лишнего этап видит готовую хижину");
        assertTrue(GraphStageGate.straysReady(repaired), "Пустой список лишнего закрывает этап по уборке");
    }

    private static GraphFacts.Tool wornShears() {
        return new GraphFacts.Tool("minecraft:shears", "shears", 0, 1, 100);
    }
}

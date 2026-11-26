package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossActions.supplyTarget;
import static com.theminespirit.core.board.graph.CrossScene.ahead;
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
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CrossSettlementScenarioPart3Tests {
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
        GraphPlan.Task repair = task(plan, "event:field_empty:repair:wheat_field");
        assertTrue(
                seeds.feasible(),
                "Без урожая граф идёт за семенами, а не кормит скот воздухом:  Иначе тест husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing перестанет ловить эту поломку. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь это утверждение."
                        + ids(plan));
        assertFalse(
                feed.feasible(),
                "Кормить животных нечем: feed_animals ждёт урожай:  Иначе тест husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing перестанет ловить эту поломку. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь это утверждение."
                        + ids(plan));
        assertEquals(
                BoardCase.REPAIR_BUILDING,
                repair.boardCase(),
                "Пустое поле восстанавливается ремонтом схемы, а не ручным посевом: иначе пшеница снова "
                        + "появится в обход wheat_field_1. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing "
                        + "проверь farm.json field_empty.");
        assertTrue(
                plan.task("plant_crop").isEmpty(),
                "Ручной посев удалён из графа: иначе без семян останется невыполнимый legacy-узел. При смене "
                        + "husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь farm.json ways.");
        assertTrue(
                seeds.priority() > feed.priority(),
                "Семена важнее невыполнимого кормления:  Иначе тест husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing перестанет ловить эту поломку. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь это утверждение."
                        + ids(plan));
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, supplyTarget("wheat_seeds")).stream()
                        .anyMatch(script ->
                                Set.of("gather", "acquire", "harvest_crop").contains(script)),
                "Семена добываются сбором, а не крафтом из воздуха: "
                        + scripts(
                                BoardCase.GATHER_MATERIALS,
                                supplyTarget(
                                        "wheat_seeds Иначе тест husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing перестанет ловить эту поломку. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь это утверждение.")));
        assertEquals(
                BoardCase.ACTIVITY_FORAGE,
                BoardActivities.caseOfScript("gather").orElseThrow(),
                "Сбор семян — действие заготовки, а не животноводства Иначе тест husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing перестанет ловить эту поломку. При смене husbandry_StarvingAnimalsNoCrops_SeedsOutrankFeedingAndSowing проверь это утверждение.");
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
        assertTrue(
                coal.feasible(),
                "Тьма в шахте сначала ведёт к углю:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(first));
        assertFalse(
                light.feasible(),
                "Свет без факела не ставится:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(first));
        assertFalse(
                torch.feasible(),
                "Факел без угля не крафтится:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(first));
        world.with("coal", 8);
        GraphPlan second = plan(world, facts);
        assertTrue(
                task(second, "event:more5_mine_dark:more5_craft_mine_torch").feasible(),
                "Уголь появился — граф ставит крафт факела:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(second));
        assertFalse(
                task(second, "event:more5_mine_dark:more5_light_mine").feasible(),
                "Пока факела нет, свет не ставится:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(second));
        world.with("torch", 8);
        GraphPlan third = plan(world, facts);
        assertTrue(
                task(third, "event:more5_mine_dark:more5_light_mine").feasible(),
                "Факел есть — граф разрешает осветить шахту:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + ids(third));
        assertTrue(
                scripts(BoardCase.CRAFT_TORCHES).contains("craft"),
                "Крафт факелов идёт скриптом craft:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + scripts(BoardCase.CRAFT_TORCHES));
        assertTrue(
                scripts(BoardCase.LIGHTING).contains("place_block"),
                "Свет ставится блоками place_block:  Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение."
                        + scripts(BoardCase.LIGHTING));
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, supplyTarget("coal")).stream()
                        .anyMatch(script ->
                                Set.of("mine_vein", "dig_area", "safe_mine").contains(script)),
                "Уголь добывается шахтным действием: "
                        + scripts(
                                BoardCase.GATHER_MATERIALS,
                                supplyTarget(
                                        "coal Иначе тест mining_DarkMine_CoalThenTorchThenLightInGraphOrder перестанет ловить эту поломку. При смене mining_DarkMine_CoalThenTorchThenLightInGraphOrder проверь это утверждение.")));
    }

    @Test
    void repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool() {
        GraphWorld stageOneWorld = stageOne();
        GraphFacts worn = facts(named(spirit(new CrossScene.SpiritSpec(
                "spirit-1", 20, 20, false, false, null, List.of(tool("pickaxe", 1, 1, 100)), List.of()))));
        GraphPlan first = plan(stageOneWorld, worn);
        GraphPlan.Task repair = task(first, "event:tool_worn:repair_tool");
        assertFalse(
                repair.feasible(),
                "Без наковальни и слитка инструмент не чинится:  Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение."
                        + ids(first));
        assertTrue(
                first.task("mine_coal").filter(GraphPlan.Task::feasible).isPresent(),
                "Изношенная кирка не останавливает шахту:  Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение."
                        + ids(first));
        GraphWorld stageTwoWorld = stageTwo();
        stageTwoWorld.built("anvil", Built.BUILT).with("iron_ingot", 8);
        GraphPlan second = plan(stageTwoWorld, worn);
        GraphPlan.Task repaired = task(second, "event:tool_worn:repair_tool");
        assertTrue(
                repaired.feasible(),
                "Наковальня и слиток есть — граф ставит ремонт кирки:  Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение."
                        + ids(second));
        assertTrue(
                repaired.raised(),
                "Ремонт инструмента имеет свой вес, а не ноль Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение.");
        assertFalse(
                scripts(BoardCase.REPAIR_TOOL).isEmpty(),
                "Ремонт инструмента имеет план действий, а не пустую карточку Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение.");
        assertEquals(
                BoardCase.ACTIVITY_CRAFT,
                BoardActivities.caseOfScript("repair_tool").orElseThrow(),
                "Ремонт инструмента — действие крафта, а не боя Иначе тест repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool перестанет ловить эту поломку. При смене repair_WornPickaxe_NeedsAnvilBeforeGraphRepairsTool проверь это утверждение.");
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
        assertEquals(
                List.of(),
                ahead(plan, defend),
                "Мобы у раненого духа — защита на потолке:  Иначе тест combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair перестанет ловить эту поломку. При смене combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repair.feasible(),
                "Побитый забор остаётся выполнимым ремонтом:  Иначе тест combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair перестанет ловить эту поломку. При смене combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair проверь это утверждение."
                        + ids(plan));
        assertTrue(
                defend.priority() > repair.priority(),
                "Сначала бой, потом забор:  Иначе тест combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair перестанет ловить эту поломку. При смене combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair проверь это утверждение."
                        + ids(plan));
        assertTrue(
                scripts(BoardCase.BUILD_FENCE).contains("build_schematic"),
                "Забор ставится схемой build_schematic:  Иначе тест combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair перестанет ловить эту поломку. При смене combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair проверь это утверждение."
                        + scripts(BoardCase.BUILD_FENCE));
        assertTrue(
                scripts(BoardCase.REPAIR_BUILDING).contains("build_schematic"),
                "Ремонт постройки использует ту же схему, а не отдельный чинт:  Иначе тест combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair перестанет ловить эту поломку. При смене combat_BrokenFenceUnderRaid_DefendOutranksFenceRepair проверь это утверждение."
                        + scripts(BoardCase.REPAIR_BUILDING));
    }
}

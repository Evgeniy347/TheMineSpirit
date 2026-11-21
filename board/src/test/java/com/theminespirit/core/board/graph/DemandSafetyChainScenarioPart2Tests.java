package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandSafetyChainScenarioPart2Tests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    private static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    private static GraphWorld pair(GraphWorld world) {
        world.tools("spirit-2", 1);
        return world;
    }

    @Test
    void case31_FloodedMine_BlocksTorchesAndBypassRaised() {
        GraphWorld world = pair(stageTwo())
                .event("more2_hazard_flow")
                .event("more5_mine_dark")
                .event("more5_route_blocked");
        world.with("cobblestone", 0)
                .with("dirt", 0)
                .with("torch", 0)
                .with("coal", 0)
                .with("oak_planks", 0);
        GraphPlan plan = plan(world, facts(TWO));
        assertTrue(
                task(plan, "event:more2_hazard_flow:more2_block_flow").raised(),
                "Затопление поднимает перекрытие:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "event:more5_mine_dark:more5_light_mine").raised(),
                "Тёмная шахта поднимает свет:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "event:more5_route_blocked:more5_bridge_route").raised(),
                "Обход затопления поднимает мост:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "craft_torch").raised(),
                "Факелов нет — крафт факелов поднят:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_cobble").raised(),
                "Блоков нет — камень поднят:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(plan));
        world.with("cobblestone", 160)
                .with("dirt", 160)
                .with("torch", 256)
                .with("coal", 128)
                .with("oak_planks", 128);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:more5_mine_dark:more5_light_mine").feasible(),
                "Свет есть — шахта освещается:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                task(stocked, "event:more5_route_blocked:more5_bridge_route").feasible(),
                "Доски есть — обход строится:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_torch").isEmpty(),
                "Факелов хватает — крафт снят:  Иначе тест case31_FloodedMine_BlocksTorchesAndBypassRaised перестанет ловить эту поломку. При смене case31_FloodedMine_BlocksTorchesAndBypassRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case32_Collapse_SandAndGravelSupportRaised() {
        GraphWorld world = pair(stageTwo()).event("sand_collapse").event("gravel_collapse");
        world.with("sand", 0).with("gravel", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task sand = task(plan, "event:sand_collapse:fix_sand_collapse");
        GraphPlan.Task gravel = task(plan, "event:gravel_collapse:fix_gravel_collapse");
        assertFalse(
                sand.feasible(),
                "Песок нужен для опоры — без него не поставить:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(plan));
        assertFalse(
                gravel.feasible(),
                "Гравий нужен для опоры — без него не поставить:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "dig_sand").raised(),
                "Песка нет — поднимается добыча песка:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "dig_gravel").raised(),
                "Гравия нет — поднимается добыча гравия:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(plan));
        world.with("sand", 64).with("gravel", 64);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:sand_collapse:fix_sand_collapse").feasible(),
                "Песок есть — опора ставится:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                task(stocked, "event:gravel_collapse:fix_gravel_collapse").feasible(),
                "Гравий есть — опора ставится:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("dig_sand").isEmpty(),
                "Песка хватает — добыча снята:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("dig_gravel").isEmpty(),
                "Гравия хватает — добыча снята:  Иначе тест case32_Collapse_SandAndGravelSupportRaised перестанет ловить эту поломку. При смене case32_Collapse_SandAndGravelSupportRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case41_FireInSettlement_WaterAndBlocksRaised() {
        GraphWorld world = pair(stageTwo()).event("more2_fire_near_building").event("fire");
        world.with("water_bucket", 0).with("bucket", 0).with("cobblestone", 0).with("dirt", 0);
        GraphPlan plan = plan(world, facts(TWO));
        assertTrue(
                task(plan, "event:more2_fire_near_building:more2_put_out_fire").feasible(),
                "Пожар — тушить выполнимо:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "fill_water_bucket").raised(),
                "Воды нет — поднимается ведро воды:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "craft_bucket").raised(),
                "Ведра нет — поднимается крафт ведра:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_cobble").raised(),
                "Блоков нет — поднимается камень:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(plan));
        world.with("water_bucket", 8).with("bucket", 4).with("cobblestone", 160).with("dirt", 160);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("fill_water_bucket").isEmpty(),
                "Воды хватает — набрать ещё не надо:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_bucket").isEmpty(),
                "Вёдра есть — крафт снят:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("mine_cobble").isEmpty(),
                "Камня хватает — шахта снята:  Иначе тест case41_FireInSettlement_WaterAndBlocksRaised перестанет ловить эту поломку. При смене case41_FireInSettlement_WaterAndBlocksRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case47_FenceBrokenNight_RepairRaisedAboveGuard() {
        GraphWorld world = pair(stageTwo()).event("fence_broken").event("night");
        world.with("oak_log", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task repair = task(plan, "event:fence_broken:repair_fence");
        GraphPlan.Task guard = task(plan, "event:night:more4_guard_settlement");
        assertFalse(
                repair.feasible(),
                "Без брёвен забор не починить:  Иначе тест case47_FenceBrokenNight_RepairRaisedAboveGuard перестанет ловить эту поломку. При смене case47_FenceBrokenNight_RepairRaisedAboveGuard проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repair.priority() > guard.priority(),
                "Сломанный забор важнее обычной охраны до ночи: забор " + repair.priority()
                        + ", охрана  Иначе тест case47_FenceBrokenNight_RepairRaisedAboveGuard перестанет ловить эту поломку. При смене case47_FenceBrokenNight_RepairRaisedAboveGuard проверь это утверждение."
                        + guard.priority());
        assertTrue(
                task(plan, "chop_oak_log").raised(),
                "Брёвен нет — поднимается рубка:  Иначе тест case47_FenceBrokenNight_RepairRaisedAboveGuard перестанет ловить эту поломку. При смене case47_FenceBrokenNight_RepairRaisedAboveGuard проверь это утверждение."
                        + ids(plan));
        world.with("oak_log", 64);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:fence_broken:repair_fence").feasible(),
                "Брёвна есть — забор чинится:  Иначе тест case47_FenceBrokenNight_RepairRaisedAboveGuard перестанет ловить эту поломку. При смене case47_FenceBrokenNight_RepairRaisedAboveGuard проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("chop_oak_log").isEmpty(),
                "Брёвен хватает — рубка снята:  Иначе тест case47_FenceBrokenNight_RepairRaisedAboveGuard перестанет ловить эту поломку. При смене case47_FenceBrokenNight_RepairRaisedAboveGuard проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case48_CropsDark_TorchesRaisedUntilLit() {
        GraphWorld world = pair(stageTwo()).event("more5_crops_dark");
        world.with("torch", 0).with("coal", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task light = task(plan, "event:more5_crops_dark:more5_light_crops");
        assertFalse(
                light.feasible(),
                "Факелов нет — грядки не осветить:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "craft_torch").raised(),
                "Темнота у грядок поднимает факелы:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_coal").raised(),
                "Угля нет — поднимается шахта угля:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(plan));
        world.with("torch", 256).with("coal", 128);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:more5_crops_dark:more5_light_crops").feasible(),
                "Факелы есть — грядки освещаются:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_torch").isEmpty(),
                "Факелов хватает — крафт снят:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("mine_coal").isEmpty(),
                "Угля хватает — шахта снята:  Иначе тест case48_CropsDark_TorchesRaisedUntilLit перестанет ловить эту поломку. При смене case48_CropsDark_TorchesRaisedUntilLit проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case49_NoBucket_IronChainRaisedThenBucketCrafted() {
        GraphWorld world = pair(stageTwo());
        world.with("water_bucket", 0).with("bucket", 0).with("iron_ingot", 0).with("raw_iron", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task fill = task(plan, "fill_water_bucket");
        GraphPlan.Task craft = task(plan, "craft_bucket");
        assertFalse(
                fill.feasible(),
                "Ведра нет — набрать воду нечем:  Иначе тест case49_NoBucket_IronChainRaisedThenBucketCrafted перестанет ловить эту поломку. При смене case49_NoBucket_IronChainRaisedThenBucketCrafted проверь это утверждение."
                        + ids(plan));
        assertTrue(
                craft.raised(),
                "Крафт ведра поднят:  Иначе тест case49_NoBucket_IronChainRaisedThenBucketCrafted перестанет ловить эту поломку. При смене case49_NoBucket_IronChainRaisedThenBucketCrafted проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_iron").raised() || task(plan, "smelt_iron").raised(),
                "Железа нет — поднимается железная цепочка:  Иначе тест case49_NoBucket_IronChainRaisedThenBucketCrafted перестанет ловить эту поломку. При смене case49_NoBucket_IronChainRaisedThenBucketCrafted проверь это утверждение."
                        + ids(plan));
        world.with("bucket", 4).with("iron_ingot", 8).with("raw_iron", 8);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "fill_water_bucket").feasible(),
                "Ведро есть — вода набирается:  Иначе тест case49_NoBucket_IronChainRaisedThenBucketCrafted перестанет ловить эту поломку. При смене case49_NoBucket_IronChainRaisedThenBucketCrafted проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_bucket").isEmpty(),
                "Ведра хватает — крафт снят:  Иначе тест case49_NoBucket_IronChainRaisedThenBucketCrafted перестанет ловить эту поломку. При смене case49_NoBucket_IronChainRaisedThenBucketCrafted проверь это утверждение."
                        + ids(stocked));
    }
}

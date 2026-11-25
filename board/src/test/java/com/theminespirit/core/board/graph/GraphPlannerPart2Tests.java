package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphPlannerPart2Tests {
    @Test
    void plan_StorageFullWarehouseNotBuilt_TaskPlanned() {
        GraphWorld world = GraphWorld.settled();
        world.storageFree = 5;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("building:warehouse").isPresent(),
                "Склад первого уровня в плане этапа 1: при нехватке места дух строит склад сам, без просьбы стратегу: "
                        + plan.tasks().stream().map(GraphPlan.Task::id).toList()
                        + " При смене plan_StorageFullWarehouseNotBuilt_TaskPlanned проверь это утверждение.");
    }

    @Test
    void plan_StockAboveThreshold_NoGatherTask() {
        GraphPlan plan = GraphWorld.settled().with("dirt", 1000).plan();
        assertTrue(
                !plan.raised("dig_dirt"),
                "Земли больше Xmin·P·S·K — задания «добыть землю» нет: "
                        + plan.task(
                                "dig_dirt Иначе тест plan_StockAboveThreshold_NoGatherTask перестанет ловить эту поломку. При смене plan_StockAboveThreshold_NoGatherTask проверь это утверждение."));
    }

    @Test
    void plan_WarehouseBuiltStorageFull_NoticeNotRebuild() {
        GraphWorld world = GraphWorld.settled().built("warehouse", Built.BUILT);
        world.storageFree = 5;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("building:warehouse").isEmpty()
                        && plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Склад построен, а места нет — не строим склад заново по кругу, просим стратега расширить: "
                        + plan.task("building:warehouse")
                        + ",  Иначе тест plan_WarehouseBuiltStorageFull_NoticeNotRebuild перестанет ловить эту поломку. При смене plan_WarehouseBuiltStorageFull_NoticeNotRebuild проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_WoodenHoeAndSword_OnlySwordUpgraded() {
        GraphPlan plan = GraphWorld.settled().tools("spirit-1", 0).plan();
        assertTrue(
                plan.task("craft_stone_hoe@spirit-1").isEmpty()
                        && plan.task("craft_stone_sword@spirit-1").isPresent(),
                "Набор по видам: мотыге хватает деревянной, мечу нужен каменный — "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(
                                        id -> id.contains("hoe")
                                                || id.contains(
                                                        "sword Иначе тест plan_WoodenHoeAndSword_OnlySwordUpgraded перестанет ловить эту поломку. При смене plan_WoodenHoeAndSword_OnlySwordUpgraded проверь это утверждение."))
                                .toList());
    }

    @Test
    void plan_CactusNear_ClearTaskAndKeepDistance() {
        GraphPlan plan = GraphWorld.settled().event("cactus_near").plan();
        assertTrue(
                plan.task("event:cactus_near:clear_cactus").isPresent()
                        && plan.advice().contains(new GraphPlan.Advice("cactus_near", "keep_distance_from_cactus")),
                "Кактус рядом — задание убрать с участка и совет тактике держаться на расстоянии:  Иначе тест plan_CactusNear_ClearTaskAndKeepDistance перестанет ловить эту поломку. При смене plan_CactusNear_ClearTaskAndKeepDistance проверь это утверждение."
                        + plan.advice());
    }

    @Test
    void plan_CreeperCraterNoDirt_FillTaskRaisesDigging() {
        GraphPlan calm = GraphWorld.settled().with("dirt", 0).plan();
        GraphPlan crater =
                GraphWorld.settled().with("dirt", 0).event("creeper_crater").plan();
        assertTrue(
                crater.task("event:creeper_crater:fill_crater").isPresent()
                        && crater.priority("dig_dirt") > calm.priority("dig_dirt"),
                "Воронка без земли — задание засыпать и земля важнее: " + crater.priority("dig_dirt") + " против "
                        + calm.priority(
                                "dig_dirt Иначе тест plan_CreeperCraterNoDirt_FillTaskRaisesDigging перестанет ловить эту поломку. При смене plan_CreeperCraterNoDirt_FillTaskRaisesDigging проверь это утверждение."));
    }

    @Test
    void plan_LavaSeen_RaisesWaterBucket() {
        GraphWorld base = GraphWorld.settled();
        base.stage = 2;
        GraphPlan calm = base.copy().plan();
        GraphPlan lava = base.copy().event("lava_seen").plan();
        assertTrue(
                lava.priority("fill_water_bucket") > calm.priority("fill_water_bucket"),
                "Встретили лаву — ведро воды важнее: " + lava.priority("fill_water_bucket") + " против "
                        + calm.priority(
                                "fill_water_bucket Иначе тест plan_LavaSeen_RaisesWaterBucket перестанет ловить эту поломку. При смене plan_LavaSeen_RaisesWaterBucket проверь это утверждение."));
    }

    @Test
    void plan_HostileNearby_RaisesSword() {
        GraphPlan calm = GraphWorld.settled().tools("spirit-1", 0).plan();
        GraphPlan hostile = GraphWorld.settled()
                .tools("spirit-1", 0)
                .event("hostile_nearby")
                .plan();
        assertTrue(
                hostile.priority("craft_stone_sword@spirit-1") > calm.priority("craft_stone_sword@spirit-1"),
                "Враги рядом — меч важнее: " + hostile.priority("craft_stone_sword@spirit-1") + " против "
                        + calm.priority(
                                "craft_stone_sword@spirit-1 Иначе тест plan_HostileNearby_RaisesSword перестанет ловить эту поломку. При смене plan_HostileNearby_RaisesSword проверь это утверждение."));
    }

    @Test
    void plan_NetherAndEnd_ChainsMarkedInDevelopment() {
        GraphWorld world = GraphWorld.settled()
                .built("smithing_table", Built.BUILT)
                .with("netherite_scrap", 8)
                .with("gold_ingot", 8)
                .with("blaze_powder", 4)
                .with("ender_pearl", 4);
        world.stage = 4;
        world.world.add("nether");
        world.world.add("end");
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("craft_netherite_ingot")
                                .map(task -> !task.implemented())
                                .orElse(false)
                        && plan.task("craft_ender_eye")
                                .map(task -> !task.implemented())
                                .orElse(false),
                "Незерит и око Края стоят на доске как «в разработке»: " + plan.task("craft_netherite_ingot") + ", "
                        + plan.task(
                                "craft_ender_eye Иначе тест plan_NetherAndEnd_ChainsMarkedInDevelopment перестанет ловить эту поломку. При смене plan_NetherAndEnd_ChainsMarkedInDevelopment проверь это утверждение."));
    }

    @Test
    void plan_FoodNorm_PerSpirit() {
        GraphWorld one = GraphWorld.settled();
        GraphWorld two = GraphWorld.settled().tools("spirit-2", 1);
        SupplyGraph.Item food = GraphWorld.GRAPH.items().get("food");

        assertTrue(
                food.perSpirit(),
                "Запас еды задаётся нормой на духа: иначе восемь единиц на всё поселение кажутся"
                        + " достаточными, пока шесть голодных ртов ждут работы Иначе тест plan_FoodNorm_PerSpirit"
                        + " перестанет ловить эту поломку. При смене plan_FoodNorm_PerSpirit проверь graph.json food.");
        assertEquals(
                GraphLack.threshold(food, one.state()) * 2,
                GraphLack.threshold(food, two.state()),
                "Два духа требуют вдвое больше еды, как и брони: иначе рацион не масштабируется"
                        + " вместе с поселением Иначе тест plan_FoodNorm_PerSpirit перестанет ловить эту"
                        + " поломку. При смене plan_FoodNorm_PerSpirit проверь GraphLack.threshold.");
    }

    @Test
    void plan_ArmorNorms_PerSpiritAndByStage() {
        GraphWorld one = GraphWorld.settled();
        one.stage = 3;
        GraphWorld two = GraphWorld.settled().tools("spirit-2", 1);
        two.stage = 3;
        SupplyGraph.Item diamond = GraphWorld.GRAPH.items().get("diamond_armor");
        SupplyGraph.Item netherite = GraphWorld.GRAPH.items().get("netherite_armor");
        assertEquals(
                GraphLack.threshold(diamond, one.state()) * 2,
                GraphLack.threshold(diamond, two.state()),
                "Норма алмазной брони — на духа: два духа требуют вдвое больше Иначе тест plan_ArmorNorms_PerSpiritAndByStage перестанет ловить эту поломку. При смене plan_ArmorNorms_PerSpiritAndByStage проверь это утверждение.");
        assertTrue(
                diamond.perSpirit() && netherite.stage() == 4,
                "Броня задана нормой на духа и открывается по этапам: " + diamond
                        + ",  Иначе тест plan_ArmorNorms_PerSpiritAndByStage перестанет ловить эту поломку. При смене plan_ArmorNorms_PerSpiritAndByStage проверь это утверждение."
                        + netherite);
    }
}

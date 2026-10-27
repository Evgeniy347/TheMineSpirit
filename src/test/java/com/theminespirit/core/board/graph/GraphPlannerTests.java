package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphPlannerTests {
    @Test
    void plan_NoCoal_PostsMineAndCharcoal() {
        GraphPlan plan = GraphWorld.settled().with("coal", 0).plan();

        assertTrue(
                plan.task("mine_coal").isPresent()
                        && plan.task("smelt_charcoal").isPresent(),
                "Нет угля — на доске оба пути: «добыть каменный уголь» и «добыть древесный уголь», получено "
                        + plan.tasks());
    }

    @Test
    void plan_NoTorches_RaisesCoalWays() {
        GraphPlan stocked =
                GraphWorld.settled().with("coal", 0).with("torch", 64).plan();
        GraphPlan dark = GraphWorld.settled().with("coal", 0).with("torch", 0).plan();

        assertTrue(
                dark.priority("mine_coal") > stocked.priority("mine_coal")
                        && dark.priority("smelt_charcoal") > stocked.priority("smelt_charcoal"),
                "Нет факелов — уголь обоими путями важнее: " + dark.priority("mine_coal") + " против "
                        + stocked.priority("mine_coal"));
    }

    @Test
    void plan_NoIronPickaxe_LowersMineCoal() {
        GraphPlan iron =
                GraphWorld.settled().tools("spirit-1", 2).with("coal", 0).plan();
        GraphPlan stone = GraphWorld.settled().with("coal", 0).plan();

        assertTrue(
                stone.priority("mine_coal") < iron.priority("mine_coal"),
                "Нет железной кирки — каменный уголь ниже: " + stone.priority("mine_coal") + " против "
                        + iron.priority("mine_coal"));
    }

    @Test
    void plan_NoStonePickaxe_RaisesCobbleAndSticks() {
        GraphPlan has =
                GraphWorld.settled().with("cobblestone", 0).with("stick", 0).plan();
        GraphPlan lacks = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("cobblestone", 0)
                .with("stick", 0)
                .plan();

        assertTrue(
                lacks.priority("mine_cobble") > has.priority("mine_cobble")
                        && lacks.priority("craft_stick") > has.priority("craft_stick"),
                "Нет каменной кирки — булыжник и палки важнее: " + lacks.tasks());
    }

    @Test
    void plan_NoWoodenTools_RaisesLogAndSticks() {
        GraphPlan has = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("oak_log", 0)
                .with("oak_planks", 0)
                .with("stick", 0)
                .plan();
        GraphPlan lacks = GraphWorld.settled()
                .tools("spirit-1", -1)
                .with("oak_log", 0)
                .with("oak_planks", 0)
                .with("stick", 0)
                .plan();

        assertTrue(
                lacks.priority("chop_oak_log") > has.priority("chop_oak_log")
                        && lacks.priority("craft_stick") > has.priority("craft_stick"),
                "Нет деревянной кирки и топора — дерево и палки важнее: " + lacks.priority("chop_oak_log") + " против "
                        + has.priority("chop_oak_log"));
    }

    @Test
    void plan_LogLacking_AxesOrderedIronStoneWooden() {
        GraphPlan plan = GraphWorld.empty().with("oak_log", 0).plan();

        int iron = plan.priority("craft_iron_axe@spirit-1");
        int stone = plan.priority("craft_stone_axe@spirit-1");
        int wooden = plan.priority("craft_wooden_axe@spirit-1");
        assertTrue(
                iron > stone && stone > wooden,
                "Топоры: железный > каменный > деревянный: " + iron + ", " + stone + ", " + wooden);
    }

    @Test
    void issueOrder_OnlyPlanksAvailable_WoodenAxeFirstAmongAxes() {
        GraphPlan plan = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .with("oak_planks", 8)
                .with("stick", 4)
                .plan();

        String first = GraphWorld.issueOrder(plan).stream()
                .filter(task -> task.id().startsWith("craft_") && task.id().contains("_axe"))
                .findFirst()
                .map(GraphPlan.Task::id)
                .orElse("нет");
        assertEquals("craft_wooden_axe@spirit-1", first, "Делается тот топор, что можно сделать сейчас");
    }

    @Test
    void plan_LogsLackingSaplingsHeld_OrchardRaised() {
        GraphPlan without =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).plan();
        GraphPlan lacking =
                GraphWorld.settled().with("oak_log", 0).with("oak_sapling", 4).plan();

        assertTrue(
                lacking.priority("building:orchard_oak") > without.priority("building:orchard_oak"),
                "Брёвен нет, саженцы есть — сад важнее: " + lacking.priority("building:orchard_oak") + " против "
                        + without.priority("building:orchard_oak"));
    }

    @Test
    void plan_StorageFullWarehouseNotPlanned_NoticeForStrategist() {
        GraphWorld world = GraphWorld.settled();
        world.storageFree = 5;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Склад полон, склада нет в плане — просьба стратегу: " + plan.notices());
    }

    @Test
    void plan_StockAboveThreshold_NoGatherTask() {
        GraphPlan plan = GraphWorld.settled().with("dirt", 1000).plan();

        assertTrue(
                plan.task("dig_dirt").isEmpty()
                        || plan.task("dig_dirt").orElseThrow().priority() == 0,
                "Земли больше Xmin·P·S·K — задания «добыть землю» нет: " + plan.task("dig_dirt"));
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
                        + plan.task("building:warehouse") + ", " + plan.notices());
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
                                .filter(id -> id.contains("hoe") || id.contains("sword"))
                                .toList());
    }

    @Test
    void plan_CactusNear_ClearTaskAndKeepDistance() {
        GraphPlan plan = GraphWorld.settled().event("cactus_near").plan();

        assertTrue(
                plan.task("event:cactus_near:clear_cactus").isPresent()
                        && plan.advice().contains(new GraphPlan.Advice("cactus_near", "keep_distance_from_cactus")),
                "Кактус рядом — задание убрать с участка и совет тактике держаться на расстоянии: " + plan.advice());
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
                        + calm.priority("dig_dirt"));
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
                        + calm.priority("fill_water_bucket"));
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
                        + calm.priority("craft_stone_sword@spirit-1"));
    }

    @Test
    void plan_StageThree_PotionTasksMarkedUnimplemented() {
        GraphWorld world = GraphWorld.settled().event("lava_seen");
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("brew_fire_resistance")
                        .map(task -> !task.implemented())
                        .orElse(false),
                "Зелья ещё не реализованы — задание стоит с пометкой «не реализовано»: "
                        + plan.task("brew_fire_resistance"));
    }

    @Test
    void plan_StageThree_FullPotionLineInDevelopment() {
        GraphWorld world = GraphWorld.settled()
                .built("brewing_stand", Built.BUILT)
                .with("water_bottle", 8)
                .with("nether_wart", 8)
                .with("ghast_tear", 2)
                .with("blaze_powder", 2)
                .with("gunpowder", 2);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("brew_regeneration").map(task -> !task.implemented()).orElse(false)
                        && plan.task("splash_regeneration")
                                .map(task -> !task.implemented())
                                .orElse(false),
                "Регенерация и взрывное зелье стоят на доске с пометкой «в разработке»: "
                        + plan.task("brew_regeneration") + ", " + plan.task("splash_regeneration"));
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
                        + plan.task("craft_ender_eye"));
    }

    @Test
    void plan_Redstone_MachinesInDevelopment() {
        GraphWorld world = GraphWorld.settled()
                .with("iron_ingot", 32)
                .with("redstone", 16)
                .with("stone", 16)
                .with("cobblestone", 16)
                .with("oak_planks", 16)
                .with("stick", 16);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("craft_piston").map(task -> !task.implemented()).orElse(false)
                        && plan.task("craft_rail")
                                .map(task -> !task.implemented())
                                .orElse(false),
                "Редстоун-механики (поршень, рельсы) стоят на доске как «в разработке»: " + plan.task("craft_piston")
                        + ", " + plan.task("craft_rail"));
    }

    @Test
    void plan_TradingPost_VillagerJobsInDevelopment() {
        GraphWorld world =
                GraphWorld.settled().built("trading_post", Built.BUILT).with("emerald", 8);
        world.stage = 4;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("assign_librarian").map(task -> !task.implemented()).orElse(false),
                "Занятия жителей (библиотекарь) стоят как «в разработке»: " + plan.task("assign_librarian"));
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
                GraphPlanner.threshold(diamond, one.state()) * 2,
                GraphPlanner.threshold(diamond, two.state()),
                "Норма алмазной брони — на духа: два духа требуют вдвое больше");
        assertTrue(
                diamond.perSpirit() && netherite.stage() == 4,
                "Броня задана нормой на духа и открывается по этапам: " + diamond + ", " + netherite);
    }

    @Test
    void plan_FoodAndFuel_FullLineCloses() {
        GraphWorld world = GraphWorld.settled()
                .with("milk_bucket", 4)
                .with("sugar", 8)
                .with("egg", 4)
                .with("wheat", 16)
                .with("pumpkin", 4)
                .with("beetroot", 8)
                .with("potato", 8)
                .with("raw_cod", 4)
                .with("bucket", 2);
        world.stage = 3;
        world.world.add("lava");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("craft_cake").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("craft_cookie").isPresent(),
                "Торт и печенье собираются из запасов: " + plan.task("craft_cake") + ", " + plan.task("craft_cookie"));
        assertTrue(
                plan.task("fill_lava_bucket").map(task -> !task.implemented()).orElse(false),
                "Лава в ведре — топливо с пометкой «в разработке»: " + plan.task("fill_lava_bucket"));
    }

    @Test
    void plan_StageTwo_IronPickaxeIsKitNotChance() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("craft_iron_pickaxe@spirit-1")
                                .map(task -> !task.chance())
                                .orElse(false)
                        && plan.task("mine_iron").isPresent(),
                "Этап 2 — набор железный: железная кирка — нужда набора и тянет добычу железа, а не «ждёт случая»: "
                        + plan.task("craft_iron_pickaxe@spirit-1"));
    }

    @Test
    void plan_NightLightingBuilt_NoRebuild() {
        GraphPlan plan = GraphWorld.settled()
                .built("lighting", Built.BUILT)
                .event("night")
                .plan();

        assertTrue(
                plan.task("building:lighting").isEmpty(),
                "Ночь поднимает свет, но готовое освещение не перестраивается по кругу: "
                        + plan.task("building:lighting"));
    }

    @Test
    void plan_LavaSeenWaterBucketStocked_NoMoreBuckets() {
        GraphWorld world = GraphWorld.settled().with("water_bucket", 5).event("lava_seen");
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("fill_water_bucket").isEmpty(),
                "Вёдер воды хватает — лава не заставляет делать их бесконечно: " + plan.task("fill_water_bucket"));
    }

    @Test
    void plan_HostileNearbyStoneSwordHeld_NoSwordPush() {
        GraphPlan calm = GraphWorld.settled().plan();
        GraphPlan hostile = GraphWorld.settled().event("hostile_nearby").plan();

        assertEquals(
                calm.priority("craft_stone_sword@spirit-1"),
                hostile.priority("craft_stone_sword@spirit-1"),
                "Каменный меч уже в руках (набор этапа 1) — враги не толкают меч ещё раз");
    }

    @Test
    void plan_PortalHallUnfinished_LightingPortalNotFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("flint_and_steel", 1)
                .built("portal_hall", Built.PLANNED)
                .built("portal_lit", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:portal_lit").map(task -> !task.feasible()).orElse(false)
                        && plan.task("building:portal_hall").isPresent(),
                "Зажечь портал нельзя, пока зал портала не готов на 100%: " + plan.task("building:portal_lit"));
    }

    @Test
    void plan_PortalHallBuilt_KeepDistanceAndEnterOnlyWithPlayer() {
        GraphWorld world = GraphWorld.settled().built("portal_hall", Built.BUILT);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.advice().contains(new GraphPlan.Advice("portal_hall", "keep_distance_from_portal_2"))
                        && plan.advice()
                                .contains(new GraphPlan.Advice("portal_hall", "enter_portal_only_bound_to_player")),
                "Зал портала стоит — духи держатся от портала ≥2 блоков и входят только привязанными к игроку: "
                        + plan.advice());
    }

    @Test
    void plan_PortalHallBuiltLighterHeld_LightingFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("flint_and_steel", 1)
                .built("portal_hall", Built.BUILT)
                .built("portal_lit", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:portal_lit").map(GraphPlan.Task::feasible).orElse(false),
                "Зал готов и огниво есть — задание «зажечь портал» выполнимо: " + plan.task("building:portal_lit"));
    }

    @Test
    void plan_OakAndJungleNearby_ChopsBothSpecies() {
        GraphPlan plan =
                GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0).plan();

        assertTrue(
                plan.task("chop_oak_log").isPresent()
                        && plan.task("chop_jungle_log").isPresent()
                        && plan.task("chop_birch_log").isEmpty(),
                "Рядом дуб и тропическое дерево — брёвна заготавливаются обеих пород, берёзы рядом нет — её не рубим: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(id -> id.startsWith("chop_"))
                                .toList());
    }

    @Test
    void plan_BirchSaplingHeldNoBirchOrchard_NoticeForStrategist() {
        GraphPlan plan = GraphWorld.settled().with("birch_sapling", 2).plan();

        assertTrue(
                plan.notices().stream().anyMatch(notice -> "orchard_birch".equals(notice.building())),
                "Появились саженцы берёзы, сада под берёзу в плане нет — просьба стратегу поставить сад: "
                        + plan.notices());
    }

    @Test
    void plan_JungleSaplingsOrchardPlanned_SecondOrchardBuilt() {
        GraphPlan plan = GraphWorld.settled()
                .with("jungle_sapling", 4)
                .built("orchard_jungle", Built.PLANNED)
                .plan();

        assertTrue(
                plan.task("building:orchard_oak").isPresent()
                        && plan.task("building:orchard_jungle").isPresent(),
                "Дуб и тропическое дерево — два сада, под каждую породу: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(id -> id.startsWith("building:orchard"))
                                .toList());
    }

    @Test
    void plan_OnlyJunglePlanks_SticksFromAnyPlanks() {
        GraphPlan plan = GraphWorld.settled()
                .with("oak_planks", 0)
                .with("jungle_planks", 10)
                .with("stick", 0)
                .plan();

        assertTrue(
                plan.task("craft_stick").map(GraphPlan.Task::feasible).orElse(false),
                "Палки делаются из досок любой породы: тропические доски годятся — " + plan.task("craft_stick"));
    }

    @Test
    void plan_DropsNearby_CollectTask() {
        GraphPlan plan = GraphWorld.settled().event("drops_nearby").plan();

        assertTrue(
                plan.task("event:drops_nearby:collect_drops").isPresent(),
                "В поселении и округе лежат предметы — задание собрать: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(id -> id.startsWith("event:"))
                                .toList());
    }

    @Test
    void plan_LeafDecay_CollectSaplingsTask() {
        GraphPlan plan = GraphWorld.settled().event("leaf_decay").plan();

        assertTrue(
                plan.task("event:leaf_decay:collect_saplings").isPresent(),
                "Листва опадает, падают саженцы — задание подобрать саженцы: " + plan.advice());
    }

    @Test
    void plan_OnlyCharcoal_TorchesFeasible() {
        GraphPlan plan = GraphWorld.settled()
                .with("coal", 0)
                .with("charcoal", 4)
                .with("torch", 0)
                .plan();

        assertTrue(
                plan.task("craft_torch").map(GraphPlan.Task::feasible).orElse(false),
                "Факелы — из каменного или древесного угля: один древесный годится — " + plan.task("craft_torch"));
    }

    @Test
    void plan_OnlyDeepslate_StoneToolsFeasible() {
        GraphPlan plan = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("cobblestone", 0)
                .with("cobbled_deepslate", 8)
                .plan();

        assertTrue(
                plan.task("craft_stone_pickaxe@spirit-1")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Каменные инструменты — из любого камня группы: сланец годится — "
                        + plan.task("craft_stone_pickaxe@spirit-1"));
    }

    @Test
    void plan_MixedWoolColors_BedNeedsThreeOfOneColor() {
        GraphWorld world = GraphWorld.settled().with("red_wool", 1).with("white_wool", 2);
        world.stage = 3;
        GraphPlan mixed = world.plan();
        GraphPlan red = world.copy().with("red_wool", 3).plan();

        assertTrue(
                mixed.tasks().stream().noneMatch(task -> task.id().endsWith("_bed") && task.feasible())
                        && red.task("craft_red_bed")
                                .map(GraphPlan.Task::feasible)
                                .orElse(false),
                "Кровать — три шерсти одного цвета: 1 красная + 2 белые не годятся, 3 красные — годятся");
    }

    @Test
    void plan_HutOfBirchNoBirchNearby_ExploreTask() {
        GraphWorld world = GraphWorld.settled().with("birch_planks", 0).with("birch_log", 0);
        world.materials.put("hut", Map.of("birch_planks", 30));
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("explore:tree:birch").isPresent()
                        && plan.notices().stream().anyMatch(notice -> "birch_log".equals(notice.building())),
                "Хижина из берёзы, берёзы рядом нет — задание разведать и просьба стратегу сменить породу: "
                        + plan.notices());
    }

    @Test
    void plan_MineLevelOne_IronNeedsLevelTwo() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 1).built("mine_level_2", Built.PLANNED);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("mine_iron").map(task -> !task.feasible()).orElse(false)
                        && plan.task("building:mine_level_2").isPresent(),
                "Железо — со второго уровня шахты: пока его нет, добыча ждёт, а уровень строится — "
                        + plan.task("mine_iron"));
    }

    @Test
    void plan_OneCowInPen_BreedingWaitsForPair() {
        GraphWorld world = GraphWorld.settled()
                .built("pen_cow", Built.BUILT)
                .with("herd_cow", 1)
                .with("wheat", 10);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("breed_cow").map(task -> !task.feasible()).orElse(false)
                        && plan.task("capture_cow").isPresent(),
                "Разведение — от пары: одна корова — ловим вторую, разведение ждёт — " + plan.task("breed_cow"));
    }

    @Test
    void threshold_BedPerSpirit_NotMultipliedByStage() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 3;

        assertEquals(
                1,
                GraphPlanner.threshold(GraphWorld.GRAPH.items().get("bed"), world.state()),
                "Кровать — одна на духа, без умножения на этап");
    }

    @Test
    void plan_LogsLacking_NoOrchardNoticesForAbsentSpecies() {
        GraphPlan plan =
                GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0).plan();

        assertTrue(
                plan.notices().stream().noneMatch(notice -> notice.building().startsWith("orchard_")),
                "Брёвен мало — сады пород без саженцев и не из плана не просим (8 лишних просьб стратегу): "
                        + plan.notices());
    }

    @Test
    void plan_StageOneFoodLacking_NoNoticesForLaterStageBuildings() {
        GraphPlan plan = GraphWorld.settled().with("food", 0).plan();

        assertTrue(
                plan.notices().isEmpty(),
                "Этап 1, еды мало — поле, пекарня, загон и пасека этапов 2–3 не источник и не повод просить стратега: "
                        + plan.notices());
    }

    private static GraphWorld farm() {
        GraphWorld world = GraphWorld.settled()
                .built("pen_cow", Built.BUILT)
                .built("pen_sheep", Built.BUILT)
                .built("smoker", Built.BUILT)
                .with("wheat", 20);
        world.stage = 2;
        return world;
    }

    @Test
    void plan_HerdOverPenCapacity_CullRaised() {
        GraphPlan calm = farm().with("herd_cow", 6).with("beef", 64).plan();
        GraphPlan crowded = farm().with("herd_cow", 14).with("beef", 64).plan();

        assertTrue(
                crowded.priority("cull_cow") > calm.priority("cull_cow"),
                "Коров больше вместимости загона — забой лишних важнее: " + crowded.priority("cull_cow") + " против "
                        + calm.priority("cull_cow"));
    }

    @Test
    void plan_RawMeatBelowThreshold_BreedingRaised() {
        GraphPlan stocked = farm().with("herd_cow", 4).with("beef", 64).plan();
        GraphPlan empty = farm().with("herd_cow", 4).with("beef", 0).plan();

        assertTrue(
                empty.priority("breed_cow") > stocked.priority("breed_cow"),
                "Сырого мяса меньше порога — кормление и разведение важнее: " + empty.priority("breed_cow") + " против "
                        + stocked.priority("breed_cow"));
    }

    @Test
    void plan_RawBeefHeld_CookInSmoker() {
        GraphPlan plan = farm().with("beef", 4).with("food", 0).plan();

        assertTrue(
                plan.task("cook_beef_smoker").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.priority("cook_beef_smoker") > plan.priority("cook_beef_campfire"),
                "Сырая говядина есть, еды мало — жарим, коптильня выше костра: " + plan.task("cook_beef_smoker"));
    }

    @Test
    void plan_SheepInCowPen_WoolNeedsSheepPen() {
        GraphWorld world = GraphWorld.settled().built("pen_cow", Built.BUILT).with("herd_sheep", 3);
        world.stage = 2;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("shear").map(task -> !task.feasible()).orElse(true),
                "Загоны раздельные: стричь овец можно только в овчарне — " + plan.task("shear"));
    }

    @Test
    void plan_DiamondFoundStonePickaxe_RememberAndRaisePickaxe() {
        GraphWorld world = GraphWorld.settled().event("ore_found_diamond");
        world.world.add("ore:diamond_found");
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("mine_found_diamond").map(task -> !task.feasible()).orElse(true)
                        && plan.advice().stream().anyMatch(advice -> "ore_found_diamond".equals(advice.event())),
                "Алмазная руда найдена, кирка каменная — не копаем, запоминаем, кирку поднимаем: "
                        + plan.task("mine_found_diamond"));
    }

    @Test
    void plan_DiamondFoundIronPickaxe_MineableFortuneUnimplemented() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", 2);
        world.world.add("ore:diamond_found");
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("mine_found_diamond").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("enchant_fortune_pickaxe")
                                .map(task -> !task.implemented())
                                .orElse(true),
                "С железной киркой найденные алмазы копаются; кирка «на удачу» — чары, пока не реализованы: "
                        + plan.task("mine_found_diamond"));
    }

    @Test
    void plan_OneSpiritKitted_ChestsBeforeSecondSpiritTools() {
        GraphWorld world = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .built("clearing", Built.BUILT)
                .tools("spirit-1", 1)
                .tools("spirit-2", 0)
                .tools("spirit-3", 0)
                .with("jungle_planks", 64)
                .with("stick", 16)
                .with("cobblestone", 64);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority("building:chests") > plan.priority("craft_stone_axe@spirit-2"),
                "У поселения каменный набор уже есть (spirit-1) — сундуки этапа раньше каменного набора каждому духу: "
                        + "сундуки " + plan.priority("building:chests") + ", топор spirit-2 "
                        + plan.priority("craft_stone_axe@spirit-2"));
    }

    @Test
    void plan_HutQuarterOfMaterials_StartsBuilding() {
        GraphPlan plan = GraphWorld.settled()
                .with("oak_log", 12)
                .with("oak_planks", 0)
                .with("cobblestone", 4)
                .plan();

        assertTrue(
                plan.task("building:hut").map(GraphPlan.Task::feasible).orElse(false),
                "Стройка начинается с четверти материалов (16 из 58), остальное подвозят по ходу, как в main: "
                        + plan.task("building:hut"));
    }

    @Test
    void plan_StageFour_SocialBuildingsAppearOnlyInStageFour() {
        GraphWorld before = GraphWorld.settled().built("town_hall", Built.PLANNED);
        GraphWorld after = GraphWorld.settled();
        after.stage = 4;
        for (String id : List.of("town_hall", "library", "alchemy")) {
            after.built(id, Built.PLANNED);
        }

        assertEquals(
                Optional.empty(),
                before.plan().task("building:town_hall"),
                "Ратуша — постройка четвёртого этапа: до него задания нет, иначе доска тянет камень впустую");
        assertTrue(
                after.plan().task("building:town_hall").isPresent()
                        && after.plan().task("building:library").isPresent()
                        && after.plan().task("building:alchemy").isPresent(),
                "На четвёртом этапе ратуша, библиотека и алхимия стоят на доске: "
                        + after.plan().tasks());
    }

    @Test
    void plan_StageFour_TownHallHasMaterialsFromSchema() {
        GraphWorld world = GraphWorld.settled().built("town_hall", Built.PLANNED);
        world.stage = 4;

        GraphPlan.Task hall = world.plan().task("building:town_hall").orElseThrow();
        assertTrue(
                !hall.consumes().isEmpty() && hall.consumes().containsKey("stone_bricks"),
                "Материалы ратуши берутся из схемы (каменный кирпич и прочее), а не пустой оценкой: "
                        + hall.consumes());
    }

    @Test
    void plan_StageFour_EnchantedBooksMarkedInDevelopment() {
        GraphWorld world = GraphWorld.settled()
                .built("enchanting_table", Built.BUILT)
                .built("enchant_room", Built.PLANNED)
                .with("book", 16)
                .with("lapis_lazuli", 48)
                .with("experience", 48);
        world.stage = 4;
        GraphPlan plan = world.plan();

        GraphPlan.Task book = plan.task("enchant_efficiency")
                .orElseThrow(() -> new AssertionError("Нет задания на книгу чар «эффективность»: " + plan.tasks()));
        assertTrue(
                !book.implemented() && plan.task("enchant_silk_touch").isPresent(),
                "Книги чар внесены как «в разработке» (implemented=false), но стоят на доске: " + book);
    }

    @Test
    void plan_PlayerHouse_OutranksFamilyHouses() {
        GraphWorld world =
                GraphWorld.settled().built("player_house", Built.PLANNED).built("family_house_a", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority("building:player_house") > plan.priority("building:family_house_a"),
                "Дом игрока важнее домов семей: " + plan.priority("building:player_house") + " против "
                        + plan.priority("building:family_house_a"));
    }
}

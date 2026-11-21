package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphPlannerPart6Tests {
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
    void plan_HutOfBirchNoBirchNearby_ExploreTask() {
        GraphWorld world = GraphWorld.settled().with("birch_planks", 0).with("birch_log", 0);
        world.materials.put("hut", Map.of("birch_planks", 30));
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("explore:tree:birch").isPresent()
                        && plan.notices().stream().anyMatch(notice -> "birch_log".equals(notice.building())),
                "Хижина из берёзы, берёзы рядом нет — задание разведать и просьба стратегу сменить породу:  Иначе тест plan_HutOfBirchNoBirchNearby_ExploreTask перестанет ловить эту поломку. При смене plan_HutOfBirchNoBirchNearby_ExploreTask проверь это утверждение."
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
                        + plan.task(
                                "mine_iron Иначе тест plan_MineLevelOne_IronNeedsLevelTwo перестанет ловить эту поломку. При смене plan_MineLevelOne_IronNeedsLevelTwo проверь это утверждение."));
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
                "Разведение — от пары: одна корова — ловим вторую, разведение ждёт — "
                        + plan.task(
                                "breed_cow Иначе тест plan_OneCowInPen_BreedingWaitsForPair перестанет ловить эту поломку. При смене plan_OneCowInPen_BreedingWaitsForPair проверь это утверждение."));
    }

    @Test
    void threshold_BedPerSpirit_NotMultipliedByStage() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 3;
        assertEquals(
                1,
                GraphLack.threshold(GraphWorld.GRAPH.items().get("bed"), world.state()),
                "Кровать — одна на духа, без умножения на этап Иначе тест threshold_BedPerSpirit_NotMultipliedByStage перестанет ловить эту поломку. При смене threshold_BedPerSpirit_NotMultipliedByStage проверь это утверждение.");
    }

    @Test
    void plan_LogsLacking_NoOrchardNoticesForAbsentSpecies() {
        GraphPlan plan =
                GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0).plan();
        assertTrue(
                plan.notices().stream().noneMatch(notice -> notice.building().startsWith("orchard_")),
                "Брёвен мало — сады пород без саженцев и не из плана не просим (8 лишних просьб стратегу):  Иначе тест plan_LogsLacking_NoOrchardNoticesForAbsentSpecies перестанет ловить эту поломку. При смене plan_LogsLacking_NoOrchardNoticesForAbsentSpecies проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_StageOneFoodLacking_NoNoticesForLaterStageBuildings() {
        GraphPlan plan = GraphWorld.settled().with("food", 0).plan();
        assertTrue(
                plan.notices().isEmpty(),
                "Этап 1, еды мало — поле, пекарня, загон и пасека этапов 2–3 не источник и не повод просить стратега:  Иначе тест plan_StageOneFoodLacking_NoNoticesForLaterStageBuildings перестанет ловить эту поломку. При смене plan_StageOneFoodLacking_NoNoticesForLaterStageBuildings проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_HerdOverPenCapacity_NoCullNoCapture() {
        GraphPlan crowded = farm().with("herd_cow", 14).with("beef", 64).plan();
        assertTrue(
                !crowded.raised("cull_cow") && !crowded.raised("capture_cow") && !crowded.raised("breed_cow"),
                "Коров больше лимита, мяса вдоволь — сверх лимита не ловим, не разводим и не забиваем (решение "
                        + "владельца 12.11):  Иначе тест plan_HerdOverPenCapacity_NoCullNoCapture перестанет ловить эту поломку. При смене plan_HerdOverPenCapacity_NoCullNoCapture проверь это утверждение."
                        + CrossScene.ranked(crowded));
    }

    @Test
    void plan_RawMeatBelowThreshold_BreedingRaised() {
        GraphPlan stocked = farm().with("herd_cow", 4).with("beef", 64).plan();
        GraphPlan empty = farm().with("herd_cow", 4).with("beef", 0).plan();
        assertTrue(
                empty.priority("breed_cow") > stocked.priority("breed_cow"),
                "Сырого мяса меньше порога — кормление и разведение важнее: " + empty.priority("breed_cow") + " против "
                        + stocked.priority(
                                "breed_cow Иначе тест plan_RawMeatBelowThreshold_BreedingRaised перестанет ловить эту поломку. При смене plan_RawMeatBelowThreshold_BreedingRaised проверь это утверждение."));
    }

    @Test
    void plan_RawBeefHeld_CookInSmoker() {
        GraphPlan plan = farm().with("beef", 4).with("food", 0).plan();
        assertTrue(
                plan.task("cook_beef_smoker").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.priority("cook_beef_smoker") > plan.priority("cook_beef_campfire"),
                "Сырая говядина есть, еды мало — жарим, коптильня выше костра: "
                        + plan.task(
                                "cook_beef_smoker Иначе тест plan_RawBeefHeld_CookInSmoker перестанет ловить эту поломку. При смене plan_RawBeefHeld_CookInSmoker проверь это утверждение."));
    }

    @Test
    void plan_SheepInCowPen_WoolNeedsSheepPen() {
        GraphWorld world = GraphWorld.settled().built("pen_cow", Built.BUILT).with("herd_sheep", 3);
        world.stage = 2;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("shear").map(task -> !task.feasible()).orElse(true),
                "Загоны раздельные: стричь овец можно только в овчарне — "
                        + plan.task(
                                "shear Иначе тест plan_SheepInCowPen_WoolNeedsSheepPen перестанет ловить эту поломку. При смене plan_SheepInCowPen_WoolNeedsSheepPen проверь это утверждение."));
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
                        + plan.task(
                                "mine_found_diamond Иначе тест plan_DiamondFoundStonePickaxe_RememberAndRaisePickaxe перестанет ловить эту поломку. При смене plan_DiamondFoundStonePickaxe_RememberAndRaisePickaxe проверь это утверждение."));
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
                        + plan.task(
                                "mine_found_diamond Иначе тест plan_DiamondFoundIronPickaxe_MineableFortuneUnimplemented перестанет ловить эту поломку. При смене plan_DiamondFoundIronPickaxe_MineableFortuneUnimplemented проверь это утверждение."));
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
                        + plan.priority(
                                "craft_stone_axe@spirit-2 Иначе тест plan_OneSpiritKitted_ChestsBeforeSecondSpiritTools перестанет ловить эту поломку. При смене plan_OneSpiritKitted_ChestsBeforeSecondSpiritTools проверь это утверждение."));
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
                        + plan.task(
                                "building:hut Иначе тест plan_HutQuarterOfMaterials_StartsBuilding перестанет ловить эту поломку. При смене plan_HutQuarterOfMaterials_StartsBuilding проверь это утверждение."));
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
                "Ратуша — постройка четвёртого этапа: до него задания нет, иначе доска тянет камень впустую При смене plan_StageFour_SocialBuildingsAppearOnlyInStageFour проверь это утверждение.");
        assertTrue(
                after.plan().task("building:town_hall").isPresent()
                        && after.plan().task("building:library").isPresent()
                        && after.plan().task("building:alchemy").isPresent(),
                "На четвёртом этапе ратуша, библиотека и алхимия стоят на доске:  Иначе тест plan_StageFour_SocialBuildingsAppearOnlyInStageFour перестанет ловить эту поломку. При смене plan_StageFour_SocialBuildingsAppearOnlyInStageFour проверь это утверждение."
                        + after.plan().tasks());
    }

    @Test
    void plan_StageFour_TownHallHasMaterialsFromSchema() {
        GraphWorld world = GraphWorld.settled().built("town_hall", Built.PLANNED);
        world.stage = 4;
        GraphPlan.Task hall = world.plan().task("building:town_hall").orElseThrow();
        assertTrue(
                !hall.consumes().isEmpty() && hall.consumes().containsKey("stone_bricks"),
                "Материалы ратуши берутся из схемы (каменный кирпич и прочее), а не пустой оценкой:  Иначе тест plan_StageFour_TownHallHasMaterialsFromSchema перестанет ловить эту поломку. При смене plan_StageFour_TownHallHasMaterialsFromSchema проверь это утверждение."
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
                "Книги чар внесены как «в разработке» (implemented=false), но стоят на доске:  Иначе тест plan_StageFour_EnchantedBooksMarkedInDevelopment перестанет ловить эту поломку. При смене plan_StageFour_EnchantedBooksMarkedInDevelopment проверь это утверждение."
                        + book);
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
                        + plan.priority(
                                "building:family_house_a Иначе тест plan_PlayerHouse_OutranksFamilyHouses перестанет ловить эту поломку. При смене plan_PlayerHouse_OutranksFamilyHouses проверь это утверждение."));
    }
}

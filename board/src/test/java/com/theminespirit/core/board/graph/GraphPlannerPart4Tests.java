package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphPlannerPart4Tests {
    @Test
    void plan_PlayerCraftOrder_CraftAtPlayerPriorityAndReport() {
        GraphPlan plan = GraphWorld.settled().event("player_craft_order").plan();
        assertTrue(
                plan.task("event:player_craft_order:craft_player_order").isPresent(),
                "Заказ крафта из вкладки «Крафт» — задание собрать заказ:  Иначе тест plan_PlayerCraftOrder_CraftAtPlayerPriorityAndReport перестанет ловить эту поломку. При смене plan_PlayerCraftOrder_CraftAtPlayerPriorityAndReport проверь это утверждение."
                        + plan.tasks());
        assertTrue(
                plan.advice().stream().anyMatch(advice -> "report_order_done_in_chat".equals(advice.rule()))
                        && plan.advice().stream()
                                .anyMatch(advice -> "order_shortage_as_player_priority".equals(advice.rule())),
                "Недостача заказа идёт с приоритетом игрока, о готовности дух пишет в чат:  Иначе тест plan_PlayerCraftOrder_CraftAtPlayerPriorityAndReport перестанет ловить эту поломку. При смене plan_PlayerCraftOrder_CraftAtPlayerPriorityAndReport проверь это утверждение."
                        + plan.advice());
    }

    @Test
    void plan_PlayerDiedNearBase_CollectAndReturn() {
        GraphPlan plan = GraphWorld.settled().event("player_died_near_base").plan();
        assertTrue(
                plan.task("event:player_died_near_base:collect_player_drops").isPresent()
                        && plan.task("event:player_died_near_base:return_to_base")
                                .isPresent()
                        && plan.task("event:player_died_near_base:guard_death_site")
                                .isEmpty(),
                "Ближе 100 блоков к базе — собрать вещи и вернуться, без укрытия:  Иначе тест plan_PlayerDiedNearBase_CollectAndReturn перестанет ловить эту поломку. При смене plan_PlayerDiedNearBase_CollectAndReturn проверь это утверждение."
                        + plan.tasks());
    }

    @Test
    void plan_NetherExpedition_NeedsPartyArmorPotionsFood() {
        GraphWorld world = GraphWorld.settled()
                .built("portal_lit", Built.BUILT)
                .built("route_blaze_spawner", Built.BUILT)
                .with("blaze_rod", 0);
        world.stage = 4;
        world.world.add("nether");
        world.event("nether_expedition");
        GraphPlan plan = world.plan();
        SupplyGraph.Way expedition =
                GraphWorld.GRAPH.way("expedition_blaze_rod").orElseThrow();
        assertTrue(
                plan.task("expedition_blaze_rod")
                        .map(task -> !task.implemented())
                        .orElse(false),
                "Экспедиция за стержнем ифрита стоит как «в разработке»: "
                        + plan.task(
                                "expedition_blaze_rod Иначе тест plan_NetherExpedition_NeedsPartyArmorPotionsFood перестанет ловить эту поломку. При смене plan_NetherExpedition_NeedsPartyArmorPotionsFood проверь это утверждение."));
        assertTrue(
                expedition.needs().keySet().containsAll(List.of("expedition_party", "#armor", "food")),
                "Экспедиции нужны отряд, броня на каждого, мечи, зелья и еда:  Иначе тест plan_NetherExpedition_NeedsPartyArmorPotionsFood перестанет ловить эту поломку. При смене plan_NetherExpedition_NeedsPartyArmorPotionsFood проверь это утверждение."
                        + expedition.needs());
        assertTrue(
                plan.advice().stream().anyMatch(advice -> "expedition_only_with_player".equals(advice.rule())),
                "Пока экспедиции идут только с игроком:  Иначе тест plan_NetherExpedition_NeedsPartyArmorPotionsFood перестанет ловить эту поломку. При смене plan_NetherExpedition_NeedsPartyArmorPotionsFood проверь это утверждение."
                        + plan.advice());
        assertTrue(
                !GraphWorld.GRAPH.implemented("route_end_fortress")
                        && GraphWorld.GRAPH
                                        .buildings()
                                        .get("route_end_fortress")
                                        .stage()
                                == 4,
                "Маршрут до крепости Края внесён на этап 4 и помечен «в разработке» Иначе тест plan_NetherExpedition_NeedsPartyArmorPotionsFood перестанет ловить эту поломку. При смене plan_NetherExpedition_NeedsPartyArmorPotionsFood проверь это утверждение.");
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
                        + plan.task(
                                "craft_iron_pickaxe@spirit-1 Иначе тест plan_StageTwo_IronPickaxeIsKitNotChance перестанет ловить эту поломку. При смене plan_StageTwo_IronPickaxeIsKitNotChance проверь это утверждение."));
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
                        + plan.task(
                                "building:lighting Иначе тест plan_NightLightingBuilt_NoRebuild перестанет ловить эту поломку. При смене plan_NightLightingBuilt_NoRebuild проверь это утверждение."));
    }

    @Test
    void plan_LavaSeenWaterBucketStocked_NoMoreBuckets() {
        GraphWorld world = GraphWorld.settled().with("water_bucket", 5).event("lava_seen");
        world.stage = 2;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("fill_water_bucket").isEmpty(),
                "Вёдер воды хватает — лава не заставляет делать их бесконечно: "
                        + plan.task(
                                "fill_water_bucket Иначе тест plan_LavaSeenWaterBucketStocked_NoMoreBuckets перестанет ловить эту поломку. При смене plan_LavaSeenWaterBucketStocked_NoMoreBuckets проверь это утверждение."));
    }

    @Test
    void plan_HostileNearbyStoneSwordHeld_NoSwordPush() {
        GraphPlan calm = GraphWorld.settled().plan();
        GraphPlan hostile = GraphWorld.settled().event("hostile_nearby").plan();
        assertEquals(
                calm.priority("craft_stone_sword@spirit-1"),
                hostile.priority("craft_stone_sword@spirit-1"),
                "Каменный меч уже в руках (набор этапа 1) — враги не толкают меч ещё раз Иначе тест plan_HostileNearbyStoneSwordHeld_NoSwordPush перестанет ловить эту поломку. При смене plan_HostileNearbyStoneSwordHeld_NoSwordPush проверь это утверждение.");
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
                "Зажечь портал нельзя, пока зал портала не готов на 100%: "
                        + plan.task(
                                "building:portal_lit Иначе тест plan_PortalHallUnfinished_LightingPortalNotFeasible перестанет ловить эту поломку. При смене plan_PortalHallUnfinished_LightingPortalNotFeasible проверь это утверждение."));
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
                "Зал портала стоит — духи держатся от портала ≥2 блоков и входят только привязанными к игроку:  Иначе тест plan_PortalHallBuilt_KeepDistanceAndEnterOnlyWithPlayer перестанет ловить эту поломку. При смене plan_PortalHallBuilt_KeepDistanceAndEnterOnlyWithPlayer проверь это утверждение."
                        + plan.advice());
    }
}

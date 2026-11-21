package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphPlannerPart3Tests {
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
                .with("cod", 4)
                .with("bucket", 2);
        world.stage = 3;
        world.world.add("lava");
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("craft_cake").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("craft_cookie").isPresent(),
                "Торт и печенье собираются из запасов: " + plan.task("craft_cake") + ", "
                        + plan.task(
                                "craft_cookie Иначе тест plan_FoodAndFuel_FullLineCloses перестанет ловить эту поломку. При смене plan_FoodAndFuel_FullLineCloses проверь это утверждение."));
        assertTrue(
                plan.task("fill_lava_bucket").map(task -> !task.implemented()).orElse(false),
                "Лава в ведре — топливо с пометкой «в разработке»: "
                        + plan.task(
                                "fill_lava_bucket Иначе тест plan_FoodAndFuel_FullLineCloses перестанет ловить эту поломку. При смене plan_FoodAndFuel_FullLineCloses проверь это утверждение."));
    }

    @Test
    void plan_PetsBeesComposter_AskForResources() {
        GraphWorld world = GraphWorld.settled()
                .built("kennel", Built.BUILT)
                .built("composter", Built.BUILT)
                .with("bone", 8)
                .with("wheat", 16);
        world.stage = 3;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("tame_wolf").map(GraphPlan.Task::feasible).orElse(false)
                        && plan.task("compost_crop")
                                .map(GraphPlan.Task::feasible)
                                .orElse(false),
                "Волк приручается костью, излишки урожая идут в компостер: " + plan.task("tame_wolf") + ", "
                        + plan.task(
                                "compost_crop Иначе тест plan_PetsBeesComposter_AskForResources перестанет ловить эту поломку. При смене plan_PetsBeesComposter_AskForResources проверь это утверждение."));
        assertTrue(
                plan.task("relocate_beehive").map(task -> !task.implemented()).orElse(false),
                "Перенос улья (шёлковое касание) — «в разработке»: "
                        + plan.task(
                                "relocate_beehive Иначе тест plan_PetsBeesComposter_AskForResources перестанет ловить эту поломку. При смене plan_PetsBeesComposter_AskForResources проверь это утверждение."));
    }

    @Test
    void plan_Night_HalfGuardNoBedPush() {
        GraphPlan day = GraphWorld.settled().plan();
        GraphPlan night = GraphWorld.settled().event("night").plan();
        assertTrue(
                night.advice().stream().anyMatch(advice -> "night_half_home_half_guard".equals(advice.rule()))
                        && night.advice().stream()
                                .anyMatch(advice -> "player_sleeps_world_skips".equals(advice.rule())),
                "Ночь: половина дома, половина в дозоре; сон игрока пропускает мир:  Иначе тест plan_Night_HalfGuardNoBedPush перестанет ловить эту поломку. При смене plan_Night_HalfGuardNoBedPush проверь это утверждение."
                        + night.advice());
        assertEquals(
                day.priority("craft_white_bed"),
                night.priority("craft_white_bed"),
                "Ночь больше не толкает кровать как обязательную нужду сна Иначе тест plan_Night_HalfGuardNoBedPush перестанет ловить эту поломку. При смене plan_Night_HalfGuardNoBedPush проверь это утверждение.");
    }

    @Test
    void plan_Rain_FishingAndCropsRaised() {
        GraphWorld dryWorld = GraphWorld.settled().built("field_carrot", Built.BUILT);
        dryWorld.stage = 2;
        GraphWorld rainWorld = GraphWorld.settled().built("field_carrot", Built.BUILT);
        rainWorld.stage = 2;
        rainWorld.event("rain");
        GraphPlan dry = dryWorld.plan();
        GraphPlan rain = rainWorld.plan();
        assertTrue(
                rain.priority("fish") > dry.priority("fish")
                        && rain.priority("harvest_carrot") > dry.priority("harvest_carrot"),
                "Дождь делает рыбалку и посевы выгоднее: рыба " + dry.priority("fish") + "→"
                        + rain.priority("fish") + ", морковь " + dry.priority("harvest_carrot") + "→"
                        + rain.priority(
                                "harvest_carrot Иначе тест plan_Rain_FishingAndCropsRaised перестанет ловить эту поломку. При смене plan_Rain_FishingAndCropsRaised проверь это утверждение."));
    }

    @Test
    void plan_PlayerAndBiomeEvents_PostRequestsAndPaths() {
        GraphWorld world = GraphWorld.settled().built("path_desert", Built.PLANNED);
        world.event("player_request").event("biome_found:desert");
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("event:player_request:player_request").isPresent(),
                "Просьба игрока — задание высшего приоритета: "
                        + plan.task(
                                "event:player_request:player_request Иначе тест plan_PlayerAndBiomeEvents_PostRequestsAndPaths перестанет ловить эту поломку. При смене plan_PlayerAndBiomeEvents_PostRequestsAndPaths проверь это утверждение."));
        assertTrue(
                plan.task("building:path_desert")
                        .map(GraphPlan.Task::implemented)
                        .orElse(false),
                "Открытый биом ставит тропинку до него, её механика реализована: "
                        + plan.task(
                                "building:path_desert Иначе тест plan_PlayerAndBiomeEvents_PostRequestsAndPaths перестанет ловить эту поломку. При смене plan_PlayerAndBiomeEvents_PostRequestsAndPaths проверь это утверждение."));
    }

    @Test
    void plan_SourceDepleted_AsksRouteMarker() {
        GraphPlan plan = GraphWorld.settled().event("source_depleted:sand").plan();
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "route_desert".equals(notice.building())),
                "Песок кончился — стратегу уходит просьба перенести маршрут route_desert:  Иначе тест plan_SourceDepleted_AsksRouteMarker перестанет ловить эту поломку. При смене plan_SourceDepleted_AsksRouteMarker проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_RouteRetarget_PostsRebuildTask() {
        GraphPlan plan = GraphWorld.settled().event("route_retarget_desert").plan();
        GraphPlan.Task task = plan.task("event:route_retarget_desert:rebuild_route_desert")
                .orElseThrow(() -> new AssertionError("Нет задания перестроить дорогу: " + plan.tasks()));
        assertTrue(
                task.consumes().containsKey("dirt") && task.consumes().containsKey("cobblestone"),
                "Перестройка маршрута — задание BUILD_STREETS по земле и булыжнику:  Иначе тест plan_RouteRetarget_PostsRebuildTask перестанет ловить эту поломку. При смене plan_RouteRetarget_PostsRebuildTask проверь это утверждение."
                        + task);
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "route_desert".equals(notice.building())),
                "Просьба игрока двигает точку маршрута notice-ом стратегу:  Иначе тест plan_RouteRetarget_PostsRebuildTask перестанет ловить эту поломку. При смене plan_RouteRetarget_PostsRebuildTask проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_PlayerDiedFar_CollectGuardShelter() {
        GraphPlan plan = GraphWorld.settled().event("player_died").plan();
        for (String task : List.of("collect_player_drops", "guard_death_site", "build_death_shelter")) {
            assertTrue(
                    plan.task("event:player_died:" + task).isPresent(),
                    "Гибель игрока вдали от базы — задание " + task
                            + ":  Иначе тест plan_PlayerDiedFar_CollectGuardShelter перестанет ловить эту поломку. При смене plan_PlayerDiedFar_CollectGuardShelter проверь это утверждение."
                            + plan.tasks());
        }
        assertTrue(
                plan.advice().stream().anyMatch(advice -> "death_site_bound_spirits_only".equals(advice.rule()))
                        && plan.advice().stream()
                                .anyMatch(advice -> "wait_strategist_return_or_wait_player".equals(advice.rule())),
                "Место гибели держат только духи похода, дальше решает стратег:  Иначе тест plan_PlayerDiedFar_CollectGuardShelter перестанет ловить эту поломку. При смене plan_PlayerDiedFar_CollectGuardShelter проверь это утверждение."
                        + plan.advice());
    }
}

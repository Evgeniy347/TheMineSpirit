package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandFoodChainScenarioTests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    @Test
    void case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs() {
        GraphWorld world = pair(stageTwo()).event("hungry");
        world.built("field", Built.BUILT);
        world.built("pen_cow", Built.BUILT);
        world.built("chicken_coop", Built.BUILT);
        world.with("food", 0)
                .with("wheat", 0)
                .with("bucket", 2)
                .with("herd_cow", 2)
                .with("egg", 0)
                .with("herd_chicken", 2);
        GraphPlan plan = plan(world, facts(named(spirit("spirit-1", 20, 2), spirit("spirit-2"))));

        GraphPlan.Task crop = task(plan, "harvest_wheat");
        GraphPlan.Task hunt = task(plan, "hunt_wild");
        GraphPlan.Task dairy = task(plan, "milk_cow");
        assertTrue(
                crop.feasible(),
                "Поле построено — урожай доступен первым:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(plan));
        assertTrue(
                hunt.feasible(),
                "Животные рядом — охота доступна второй:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(plan));
        assertTrue(
                dairy.feasible(),
                "Корова и ведро есть — дойка доступна, но ниже:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(plan));
        assertTrue(
                crop.priority() > dairy.priority(),
                "Урожай поднимается выше дойки: урожай " + crop.priority()
                        + ", дойка  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + dairy.priority());
        assertTrue(
                hunt.priority() > dairy.priority(),
                "Охота поднимается выше дойки: охота " + hunt.priority()
                        + ", дойка  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + dairy.priority());
        GraphPlan eggs = planWithOrders(world, facts(TWO), Map.of("egg", 1));
        GraphPlan.Task collect = task(eggs, "collect_eggs");
        assertTrue(
                collect.feasible(),
                "Курятник построен — яйца становятся источником еды:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(eggs));
        world.with("food", 64).with("wheat", 64).with("mutton", 64).with("milk_bucket", 16);
        GraphPlan full = plan(world, facts(TWO));
        assertTrue(
                full.task("craft_food").isEmpty(),
                "Еда есть — крафт еды не заказывается:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(full));
        assertTrue(
                full.task("hunt_wild").isEmpty(),
                "Мясо есть — охота не заказывается:  Иначе тест case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs перестанет ловить эту поломку. При смене case01_FoodLacking_CropAndHuntRaisedAboveDairyAndEggs проверь это утверждение."
                        + ids(full));
    }

    @Test
    void case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting() {
        GraphWorld world = pair(stageTwo()).event("hungry").with("food", 32);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(new GraphFacts.Chest(
                        new GraphFacts.Position(96, 64, 96),
                        Map.of("minecraft:bread", 32),
                        8,
                        27,
                        GraphFacts.Reach.PATH)),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        assertTrue(
                eat.feasible(),
                "Еда в дальнем достижимом сундуке — дух идёт за ней:  Иначе тест case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting перестанет ловить эту поломку. При смене case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting проверь это утверждение."
                        + ids(plan));
        assertTrue(
                plan.task("event:hungry:hunt").map(task -> !task.feasible()).orElse(true),
                "Еда есть — охота не поднимается: доставка со склада дешевле добычи:  Иначе тест case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting перестанет ловить эту поломку. При смене case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting проверь это утверждение."
                        + ids(plan));
        assertTrue(
                plan.task("craft_food").isEmpty(),
                "Запас еды покрывает цель — крафт еды не заказывается:  Иначе тест case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting перестанет ловить эту поломку. При смене case04_FoodInFarChest_DeliveryRaisedInsteadOfHunting проверь это утверждение."
                        + ids(plan));
    }

    private static GraphPlan planWithOrders(GraphWorld world, GraphFacts facts, Map<String, Integer> orders) {
        return GraphPlanner.plan(
                world.graph,
                world.state().withFacts(facts, world.graph.costs().life()).withOrders(orders));
    }

    private static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    private static GraphWorld pair(GraphWorld world) {
        world.tools("spirit-2", 1);
        return world;
    }
}

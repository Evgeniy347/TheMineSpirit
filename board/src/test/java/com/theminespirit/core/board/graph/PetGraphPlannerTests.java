package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PetGraphPlannerTests {
    @Test
    void plan_OcelotLacking_TrustWithRawFishImplemented() {
        GraphWorld world = GraphWorld.settled().with("cod", 4).with("ocelot", 0);
        world.stage = 2;
        world.world.add("biome:jungle");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("trust_ocelot").map(GraphPlan.Task::implemented).orElse(false),
                "Оцелоту не хватает — доверие сырой рыбой в джунглях стало механикой: " + plan.task("trust_ocelot")
                        + " Иначе тест plan_OcelotLacking_TrustWithRawFishImplemented перестанет ловить эту поломку. При смене plan_OcelotLacking_TrustWithRawFishImplemented проверь это утверждение.");
        assertTrue(
                GraphWorld.GRAPH.way("breed_ocelot").isPresent()
                        && GraphWorld.GRAPH.implemented("breed_ocelot")
                        && GraphWorld.GRAPH.implemented("breed_wolf"),
                "Размножение питомцев — механика, а не «в разработке»"
                        + " Иначе тест plan_OcelotLacking_TrustWithRawFishImplemented перестанет ловить эту поломку. При смене plan_OcelotLacking_TrustWithRawFishImplemented проверь это утверждение.");
    }
}

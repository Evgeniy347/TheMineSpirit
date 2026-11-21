package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DirtSupplyTests {
    @Test
    void plan_DirtShortForStreets_PostsDigDirt() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .world("soil")
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertTrue(
                dig.isPresent()
                        && dig.orElseThrow().raised()
                        && dig.orElseThrow().feasible(),
                "При нехватке земли для улиц граф обязан ставить добычу земли, получено " + plan.tasks()
                        + " Иначе тест plan_DirtShortForStreets_PostsDigDirt перестанет ловить эту поломку. При смене plan_DirtShortForStreets_PostsDigDirt проверь это утверждение.");
    }

    @Test
    void plan_QuarryInFortyBlocks_StillPostsDigDirt() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .world("soil")
                .distance("soil", 40)
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertTrue(
                dig.isPresent() && dig.orElseThrow().raised(),
                "Карьер в 40 блоках — достижимый источник земли: задание добычи обязано остаться, получено "
                        + plan.tasks()
                        + " Иначе тест plan_QuarryInFortyBlocks_StillPostsDigDirt перестанет ловить эту поломку. При смене plan_QuarryInFortyBlocks_StillPostsDigDirt проверь это утверждение.");
    }

    @Test
    void plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 3)
                .needs("streets", "dirt", 399)
                .noWorld("soil")
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertFalse(
                dig.isPresent() && dig.orElseThrow().raised(),
                "Без дёрна вне участка добыча земли невыполнима: задание не ставится, получено " + plan.tasks()
                        + " Иначе тест plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging перестанет ловить эту поломку. При смене plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging проверь это утверждение.");
        assertTrue(
                plan.notices().stream()
                        .anyMatch(notice -> "dirt".equals(notice.building())
                                && notice.reason().contains("нет источника")
                                && notice.reason().contains("soil")),
                "Нет источника земли — честный факт для стратега с причиной, получено " + plan.notices()
                        + " Иначе тест plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging перестанет ловить эту поломку. При смене plan_NoSoilAround_ReportsMissingSourceInsteadOfDigging проверь это утверждение.");
    }

    @Test
    void plan_NamespacedDirtNeed_FindsDigDirtSource() {
        GraphPlan plan = GraphWorld.empty()
                .with("dirt", 0)
                .blocks(new GraphBlock("shortage:dirt", "", new GraphBlock.Item("minecraft:dirt", 1)))
                .world("soil")
                .plan();

        Optional<GraphPlan.Task> dig = plan.task("dig_dirt");
        assertTrue(
                dig.isPresent() && dig.orElseThrow().raised(),
                "Неймспейсное 'minecraft:dirt' — та же земля: источник обязан находиться, получено " + plan.tasks()
                        + " Иначе тест plan_NamespacedDirtNeed_FindsDigDirtSource перестанет ловить эту поломку. При смене plan_NamespacedDirtNeed_FindsDigDirtSource проверь это утверждение.");
        assertFalse(
                plan.notices().stream().anyMatch(notice -> notice.reason().contains("нет источника")),
                "Ложной просьбы «нет источника» для minecraft:dirt быть не должно: " + plan.notices()
                        + " Иначе тест plan_NamespacedDirtNeed_FindsDigDirtSource перестанет ловить эту поломку. При смене plan_NamespacedDirtNeed_FindsDigDirtSource проверь это утверждение.");
    }

    @Test
    void plan_NamespacedPlanksNeed_FindsCraftSource() {
        GraphPlan plan = GraphWorld.empty()
                .with("oak_log", 4)
                .blocks(new GraphBlock("shortage:oak_planks", "", new GraphBlock.Item("minecraft:oak_planks", 8)))
                .plan();

        Optional<GraphPlan.Task> craft = plan.task("craft_oak_planks");
        assertTrue(
                craft.isPresent() && craft.orElseThrow().raised(),
                "Неймспейсные 'minecraft:oak_planks' имеют источник craft_oak_planks, получено " + plan.tasks()
                        + " Иначе тест plan_NamespacedPlanksNeed_FindsCraftSource перестанет ловить эту поломку. При смене plan_NamespacedPlanksNeed_FindsCraftSource проверь это утверждение.");
        assertFalse(
                plan.notices().stream().anyMatch(notice -> notice.reason().contains("нет источника")),
                "Ложной просьбы «нет источника» для minecraft:oak_planks быть не должно: " + plan.notices()
                        + " Иначе тест plan_NamespacedPlanksNeed_FindsCraftSource перестанет ловить эту поломку. При смене plan_NamespacedPlanksNeed_FindsCraftSource проверь это утверждение.");
    }
}

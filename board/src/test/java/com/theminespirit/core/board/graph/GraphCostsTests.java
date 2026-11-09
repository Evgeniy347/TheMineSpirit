package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.inventory.JsonResource;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphCostsTests {
    @Test
    void life_Formula_ReadFromGraphDataNotDefaults() throws IOException {
        GraphLife shipped = SupplyGraph.loadDefault().costs().life();
        assertEquals(1.5, shipped.fullPressureMobs(), "Множитель мобов формулы жизни берётся из graph.json");
        assertEquals(0.4, shipped.pressureBase(), "Базовое давление формулы жизни берётся из graph.json");
        assertEquals(0.7, shipped.pressureWound(), "Ранение формулы жизни берётся из graph.json");
        assertEquals(0.2, shipped.hurtShare(), "Свежий урон формулы жизни берётся из graph.json");

        GraphLife tuned = GraphLife.parse(new ObjectMapper().readTree("{\"pressureBase\":0.9}"));
        assertEquals(0.9, tuned.pressureBase(), "Значение из данных графа важнее встроенной константы");
        assertEquals(
                GraphLife.DEFAULTS.pressureWound(),
                tuned.pressureWound(),
                "Недостающее поле формулы жизни остаётся значением по умолчанию");
    }

    @Test
    void life_ShippedGraph_DeclaresEveryCoefficient() {
        JsonNode life = JsonResource.parse(JsonResource.readClasspath(SupplyGraph.RESOURCE), SupplyGraph.RESOURCE)
                .path("life");

        for (String name : List.of(
                "fullPressureMobs",
                "pressureBase",
                "pressureWound",
                "hurtShare",
                "injuryWound",
                "injuryHurt",
                "threatRadius",
                "damageWindowTicks",
                "weakHealth",
                "weakFood")) {
            assertTrue(
                    life.path(name).isNumber(),
                    "graph.json → life объявляет коэффициент " + name
                            + ": без него значение молча берётся из Java, а не из данных графа");
        }
    }

    @Test
    void life_NoCostsBlock_CoefficientsReadFromData() throws IOException {
        GraphCosts costs = GraphCosts.parse(new ObjectMapper().readTree("{\"life\": {\"pressureBase\": 0.9}}"));

        assertEquals(
                0.9,
                costs.life().pressureBase(),
                "Формула жизни читается из данных графа и без блока costs: иначе коэффициенты молча берутся из Java");
    }

    @Test
    void life_DefaultGraph_ReadsCoefficientsFromData() {
        GraphLife life = GraphWorld.GRAPH.costs().life();

        assertEquals(1.5, life.fullPressureMobs(), "Полное давление мобов лежит в graph.json → life");
        assertEquals(0.4, life.pressureBase(), "Базовое давление раны лежит в данных графа");
        assertEquals(0.7, life.pressureWound(), "Вклад раны в давление лежит в данных графа");
        assertEquals(0.2, life.hurtShare(), "Доля свежего урона лежит в данных графа");
        assertEquals(0.8, life.injuryWound(), "Вес жизни раненого духа лежит в данных графа");
        assertEquals(0.4, life.injuryHurt(), "Вес свежего урона духа лежит в данных графа");
        assertEquals(16.0, life.threatRadius(), "Радиус угрозы жизни лежит в graph.json → life");
        assertEquals(200, life.damageWindowTicks(), "Окно урона жизни лежит в graph.json → life");
        assertEquals(0.35, life.weakHealth(), "Порог слабого здоровья игрока лежит в данных графа");
        assertEquals(6, life.weakFood(), "Порог слабой еды игрока лежит в данных графа");
    }

    @Test
    void life_InvalidCoefficients_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 200, 1.2F, 6),
                "Доля здоровья больше 1 не бывает: порог живёт в [0, 1]");
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 0, 0.35F, 6),
                "Нулевое окно урона ломает деление: оно положительно");
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 200, 0.35F, -1),
                "Отрицательный порог еды отвергается: голод начинается с нуля");
    }

    @Test
    void plan_FarJungle_RanksBelowNearOak() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0);
        world.distances.put("tree:jungle", 120);
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority("chop_jungle_log") < plan.priority("chop_oak_log")
                        && plan.task("chop_jungle_log").orElseThrow().ticks()
                                > plan.task("chop_oak_log").orElseThrow().ticks(),
                "Тропический лес в 120 блоках — рубка дольше и ниже ближнего дуба: " + plan.task("chop_jungle_log")
                        + " / " + plan.task("chop_oak_log"));
    }

    @Test
    void plan_FurnaceOverloaded_SecondFurnaceTask() {
        GraphWorld world = GraphWorld.settled()
                .with("oak_log", 40)
                .with("coal", 10)
                .with("raw_iron", 6)
                .with("iron_ingot", 0)
                .with("sand", 20)
                .with("glass", 0)
                .with("cactus", 4);
        world.stage = 3;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("station:furnace").isPresent(),
                "Плавок больше, чем тянет одна печь (2 задания на печь) — задание поставить ещё печь: "
                        + plan.tasks().stream()
                                .filter(task ->
                                        task.feasible() && task.consumes().containsKey("#coals"))
                                .map(GraphPlan.Task::id)
                                .toList());
    }

    @Test
    void plan_HutAndPlanks_HutTakesLonger() {
        GraphPlan plan = GraphWorld.settled().with("oak_planks", 0).plan();

        assertTrue(
                plan.task("building:hut").orElseThrow().ticks()
                        > plan.task("craft_oak_planks").orElseThrow().ticks(),
                "Хижина строится дольше, чем крафтятся доски: длительность задания из costs");
    }
}

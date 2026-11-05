package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementLevelGuardTests {
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path LEVEL = Path.of("com/theminespirit/core/board/SettlementLevel.java");
    private static final Path HEIGHT = Path.of("com/theminespirit/core/board/PlotHeight.java");
    private static final Path PLOT = Path.of("com/theminespirit/core/board/SettlementPlot.java");
    private static final Path STORE = Path.of("com/theminespirit/neoforge/board/SettlementPlotStore.java");

    private static final Set<String> TERRAIN_LEVEL_CALC = Set.of(
            "PlotHeight.choose(",
            "PlotHeight.at(",
            "PlotHeight.recheck(",
            "SettlementPlot.survey(",
            "StageSites.find(",
            "StageSites.of(",
            "GroundProbe.feet(",
            "GroundProbe.top(",
            "GroundProbe.waterTop(",
            "plan.anchor().y()",
            "town.anchor().y()");

    @Test
    void settlementLevel_HeightOutsideLevel_IsReported() {
        Map<Path, String> sources = SourceFiles.javaUnder(MAIN);

        assertEquals(
                List.of(),
                violations(sources),
                "Высота поселения снова считается из рельефа мимо SettlementLevel: дом, забор, улицы, сад, шахта "
                        + "и грядка встают на разные y, и дом «повисает» над землёй. Цена: постройки не сходятся "
                        + "с планом, аудит не закрывает этап. Как надо: один раз при основании выбрать высоту в "
                        + "SettlementLevel по площади финального этапа и брать её оттуда всем. Нарушители");
    }

    @Test
    void patternSource_LevelComputedOutsideSettlementLevel_IsReported() {
        String bad = "final class FirstStepsRunner { void plot() { var p = PlotHeight.choose(area, feet, rules); } }";

        assertEquals(
                List.of("FirstStepsRunner.java: высота поселения посчитана мимо SettlementLevel"),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/FirstStepsRunner.java"), bad)),
                "Сторож ослеп: выбор высоты из рельефа мимо SettlementLevel обязан ловиться");
    }

    @Test
    void patternSource_LevelReadFromOnePlace_IsAccepted() {
        String good = "final class WorldAreaScan { void scan() { int y = SettlementLevel.of(plot); } }";

        assertEquals(
                List.of(),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/WorldAreaScan.java"), good)),
                "Сторож ложно срабатывает: чтение готовой высоты из SettlementLevel — законно");
    }

    @Test
    void patternSource_GroundProbeForBuildingHeight_IsReported() {
        String bad = "final class FirstStepsRunner { void plot() { int y = GroundProbe.feet(level, x, z, 64); } }";

        assertEquals(
                List.of("FirstStepsRunner.java: высота поселения посчитана мимо SettlementLevel"),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/FirstStepsRunner.java"), bad)),
                "Сторож ослеп: чтение рельефа под высоту постройки мимо SettlementLevel обязано ловиться");
    }

    @Test
    void patternSource_GroundProbeForStand_IsAccepted() {
        String stand = "final class GraphTargets { void spot() { int y = GroundProbe.feet(level, x, z, -1); } }";

        assertEquals(
                List.of(),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/GraphTargets.java"), stand)),
                "Сторож ложно срабатывает: стоянке и подходу рельеф читать можно");
    }

    @Test
    void patternSource_PlanAnchorHeight_IsReported() {
        String bad = "final class TownSurvey { void town(TownPlan plan) { int y = plan.anchor().y(); } }";

        assertEquals(
                List.of("TownSurvey.java: высота поселения посчитана мимо SettlementLevel"),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/TownSurvey.java"), bad)),
                "Сторож ослеп: высота плана (медиана домов) снова стала вторым источником высоты поселения");
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        sources.forEach((path, text) -> {
            String name = path.getFileName().toString();
            if (allowed(path, name)) {
                return;
            }
            String source = SourceFiles.withoutTextBlocks(text);
            boolean computes = TERRAIN_LEVEL_CALC.stream().anyMatch(source::contains);
            if (computes) {
                found.add(name + ": высота поселения посчитана мимо SettlementLevel");
            }
        });
        return found;
    }

    private static boolean allowed(Path path, String name) {
        return "SettlementLevel.java".equals(name)
                || "PlotHeight.java".equals(name)
                || "SettlementPlot.java".equals(name)
                || "SettlementPlotStore.java".equals(name)
                || "SettlementGround.java".equals(name)
                || "GroundProbe.java".equals(name)
                || "RouteWorks.java".equals(name)
                || "GraphTargets.java".equals(name)
                || "BiomeSpots.java".equals(name)
                || "GotoScript.java".equals(name)
                || "TownFixtures.java".equals(name)
                || path.equals(LEVEL)
                || path.equals(HEIGHT)
                || path.equals(PLOT)
                || path.equals(STORE);
    }

    @Test
    void settlementLevel_MissingFile_IsReported() {
        assertTrue(
                SourceFiles.javaUnder(MAIN).keySet().stream().anyMatch(path -> path.endsWith(LEVEL)),
                "Нет SettlementLevel — единственного места выбора высоты поселения");
    }
}

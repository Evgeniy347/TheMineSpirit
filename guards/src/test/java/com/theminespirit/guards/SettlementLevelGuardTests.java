package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementLevelGuardTests {
    private static final Path LEVEL = Path.of("com/theminespirit/core/board/SettlementLevel.java");
    private static final Path HEIGHT = Path.of("com/theminespirit/core/board/PlotHeight.java");
    private static final Path PLOT = SourceFiles.path("core/town/SettlementPlot.java");
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
        Map<Path, String> sources = SourceFiles.allMain();

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

    private static boolean fenceOriginFromSite(String text) {
        return text.contains("SiteLevels.fenceOrigin(");
    }

    private static boolean fenceStubCheck(String text) {
        return text.contains("FenceStubPlan.of(");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал " + file + ": " + error.getMessage(), error);
        }
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
    void fenceOrigins_DangerAndPortal_UseSettlementLevel() {
        List<String> offenders = new ArrayList<>();
        for (String file : List.of("neoforge/board/More3Post.java", "neoforge/board/More4Post.java")) {
            if (!read(SourceFiles.path(file)).contains("SiteLevels.fenceOrigin(")) {
                offenders.add(file);
            }
        }

        assertEquals(
                List.of(),
                offenders,
                "Основание забора берётся из SiteLevels.fenceOrigin (уровень ограды поселения), а не из точки "
                        + "опасности/портала: иначе забор встаёт на крышу склада (y=80), дух снизу «в яме», DigOut "
                        + "ищет путь вверх полным перебором. Нарушители");
    }

    @Test
    void fenceEvents_UseSmallStageStub_NotWholeRing() {
        List<String> offenders = new ArrayList<>();
        for (String file : List.of("neoforge/board/More3Post.java", "neoforge/board/More4Post.java")) {
            String text = read(SourceFiles.path(file));
            if (!text.contains("FenceStubPlan.of(")) {
                offenders.add(file);
            }
        }

        assertEquals(
                List.of(),
                offenders,
                "Забор по событию — маленькая заглушка ограды этапа (FenceStubPlan → StageSchemes.fenceStub → "
                        + "fence_post/wall_3_post), а не кольцо 23×23: на уровне земли кольцо вокруг опасности "
                        + "перекрывает склад и требует сотен постановок. Кольцо («fence») остаётся для REST/демо "
                        + "и задания без схемы. Нарушители");
    }

    @Test
    void fenceStubPattern_WholeRing_IsReported() {
        assertFalse(
                fenceStubCheck("TicketTarget.at(origin)"),
                "Сторож ослеп: забор по событию без своего малого фрагмента снова станет кольцом 23×23");
        assertTrue(
                fenceStubCheck("TicketTarget.at(origin).withPlan(FenceStubPlan.of(origin, stage, wood))"),
                "Сторож ложно срабатывает: фрагмент ограды этапа в опасной колонке — это правило");
    }

    @Test
    void fenceFailure_UnreachableOrigin_IsSkippedBeforeSubmit() {
        String danger = read(SourceFiles.path("neoforge/board/More3Post.java"));
        String portal = read(SourceFiles.path("neoforge/board/More4Post.java"));

        assertTrue(
                danger.indexOf("post.unreachable(at)") >= 0
                        && danger.indexOf("post.unreachable(at)") < danger.indexOf("post.submit("),
                "Забор в точке опасности не ставится снова, пока граф помнит её недостижимой (post.unreachable → "
                        + "continue): иначе DigOut повторяет полный поиск каждый заход");
        assertTrue(
                portal.indexOf("post.unreachable(origin)") >= 0
                        && portal.indexOf("post.unreachable(origin)") < portal.indexOf("post.submit("),
                "Забор у портала тоже пропускает недостижимую точку: правило общее, а не только для опасностей");
    }

    @Test
    void fenceOriginPattern_WithoutSettlementLevel_IsReported() {
        assertTrue(
                !fenceOriginFromSite("TicketTarget.at(danger.position())"),
                "Сторож ослеп: забор из точки опасности мимо SiteLevels.fenceOrigin обязан ловиться");
    }

    @Test
    void fenceOriginPattern_FromSettlementLevel_IsAccepted() {
        assertTrue(
                fenceOriginFromSite("TicketTarget.at(SiteLevels.fenceOrigin(site, danger.position()))"),
                "Сторож ложно срабатывает: забор на уровне ограды поселения — это правило");
    }

    @Test
    void settlementLevel_MissingFile_IsReported() {
        assertTrue(
                SourceFiles.allMain().keySet().stream().anyMatch(path -> path.endsWith(LEVEL)),
                "Нет SettlementLevel — единственного места выбора высоты поселения");
    }
}

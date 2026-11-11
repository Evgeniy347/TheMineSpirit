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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DigOutRuleGuardTests {
    private static final List<String> TARGET_FILES = List.of(
            "neoforge/board/DigOutAction.java", "neoforge/board/DigOutPost.java", "neoforge/board/RescueTicket.java");
    private static final List<String> HEIGHT_LIFTS = List.of("SurfacePoint", "getHeight(", "Heightmap.");
    private static final String SUPPORT = "PlaceSupport.hasSupport(";
    private static final String PLACE = "placer.begin(";
    private static final String BUILD_PIT_WATCH = "neoforge/BuildPitWatch.java";
    private static final String DIG_INPUTS = "neoforge/board/DigInputs.java";
    private static final String TRAPPED = "DigOutTrap.trapped(";

    @Test
    void digOutTarget_HomePointChosen_NoHeightmapLift() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : TARGET_FILES) {
            sources.put(name, read(SourceFiles.path(name)));
        }

        assertEquals(
                List.of(),
                heightLifts(sources),
                "Цель dig_out и спасения — стоянка дома (HomeStand/WalkableStand). SurfacePoint/Heightmap "
                        + "поднимает её на верх колонки: над домом оказывается крыша склада (-171,80,-489), и дух "
                        + "лезет наверх вместо дома. Нарушители");
    }

    @Test
    void digOutFloor_Placement_CheckedForSupportFirst() {
        String text = read(SourceFiles.path("neoforge/DigOutFloor.java"));

        assertTrue(
                supportBeforePlace(text),
                "Ступень без опоры нельзя даже пытаться поставить: иначе в block_changes появляются пустые "
                        + "постановки air → air вокруг духа после каждого dig_out NONE. Сначала PlaceSupport, потом "
                        + "placer.begin");
    }

    @Test
    void heightLiftScan_HeightmapCall_IsReported() {
        Map<String, String> sources = Map.of(
                "neoforge/board/DigOutAction.java",
                "int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);");

        assertEquals(
                List.of("neoforge/board/DigOutAction.java: getHeight(", "neoforge/board/DigOutAction.java: Heightmap."),
                heightLifts(sources),
                "Сторож ослеп: высота колонки в цели dig_out обязана попадать в нарушители");
    }

    @Test
    void heightLiftScan_HomePoint_IsAccepted() {
        Map<String, String> sources = Map.of("neoforge/board/DigOutAction.java", "start(service, spirit, settlement);");

        assertEquals(
                List.of(),
                heightLifts(sources),
                "Сторож ложно срабатывает: стоянка дома как цель — это правило, а не нарушение");
    }

    @Test
    void floorScan_PlaceBeforeSupport_IsReported() {
        assertFalse(
                supportBeforePlace(
                        "placer.begin(cell, state, id); if (PlaceSupport.hasSupport(level, cell, state)) {}"),
                "Сторож ослеп: постановка до проверки опоры обязана попадать в нарушители");
    }

    @Test
    void floorScan_SupportBeforePlace_IsAccepted() {
        assertTrue(
                supportBeforePlace(
                        "if (!PlaceSupport.hasSupport(level, cell, state)) { return Optional.of(\"нет опоры\"); } "
                                + "return switch (placer.begin(cell, state, id)) {};"),
                "Сторож ложно срабатывает: проверка опоры перед постановкой — это правило");
    }

    @Test
    void buildPit_SchematicPlacement_ChecksTrappedBeforeEscape() {
        String text = read(SourceFiles.path("neoforge/SchematicPlacement.java"));

        assertTrue(
                escapeAfterTrapped(text),
                "Стройка не запускает DigOut на любом «ниже низа схемы»: копать вверх полным поиском пути надо "
                        + "только запертому духу (BuildPit.needsEscape → pit.escape). Цена: дух на земле под крышей "
                        + "склада ищет путь на y=80, падает за 3,4 с и повторяет");
    }

    @Test
    void buildPit_TrappedCheck_IsReported() {
        assertTrue(
                !escapeAfterTrapped("if (feet.y < origin.y - 1) { host.pit.escape(feet); return RUNNING; }"),
                "Сторож ослеп: запуск DigOut без проверки запертости обязан быть нарушителем");
    }

    @Test
    void buildPit_TrappedCheck_IsAccepted() {
        assertTrue(
                escapeAfterTrapped("if (BuildPit.needsEscape(feet.y(), origin.y(), pit.trapped())) { "
                        + "host.pit.escape(feet); return RUNNING; }"),
                "Сторож ложно срабатывает: проверка BuildPit.needsEscape перед pit.escape — это правило");
    }

    @Test
    void buildPit_TrappedSpirit_UsesSharedCheck() {
        assertTrue(
                read(SourceFiles.path(BUILD_PIT_WATCH)).contains(TRAPPED),
                "Запертость стройки считается общим признаком DigOutTrap.trapped (sealed или embedded), а не "
                        + "своей копией: иначе правка признака в одном месте оставляет второе с прежним правилом, "
                        + "и любая низина снова становится ямой и копает вверх");
        assertTrue(
                read(SourceFiles.path(DIG_INPUTS)).contains(TRAPPED),
                "Вход графа dig_out обязан спрашивать тот же общий признак запертости: две копии «sealed или "
                        + "embedded» расходятся молча");
    }

    @Test
    void buildPit_DuplicateTrappedCheck_IsReported() {
        assertFalse(
                trappedCheck("DigOutScript.sealed(level, spirit.blockPosition()) || DigOutScript.embedded(spirit)"),
                "Сторож ослеп: своя копия «sealed или embedded» мимо DigOutTrap.trapped обязана ловиться");
        assertTrue(
                trappedCheck("return DigOutTrap.trapped(level, spirit);"),
                "Сторож ложно срабатывает: общий признак запертости — это правило");
    }

    @Test
    void buildPit_EscapeFailure_IsTypedGraphFact() {
        String text = read(SourceFiles.path("neoforge/BuildSchematicScript.java"));

        assertTrue(
                text.contains("pit.escapeFailed()") && text.contains("FailureFact.NO_PATH"),
                "Провал выхода из ямы обязан быть фактом на входе графа (FailureFact.NO_PATH), а не только "
                        + "текстом: иначе задание выдаётся снова и полный поиск пути повторяется");
    }

    @Test
    void homePoint_OneForReturnAndDigOut_NoStageOneSite() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : List.of(
                "neoforge/board/DigOutAction.java",
                "neoforge/board/DigOutPost.java",
                "neoforge/board/RescueGroupPost.java")) {
            sources.put(name, read(SourceFiles.path(name)));
        }
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            if ((!text.contains("HomePoint.of(") && !text.contains("homeOf("))
                    || text.contains(".site()")
                    || text.contains("SettlementHome.of(")) {
                offenders.add(file);
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "return_home и dig_out берут одну точку дома: HomePoint (дом самого духа, иначе живой этап). "
                        + "site() — это всегда первый этап, а SettlementHome.center — чужая/первая точка: выход уходил "
                        + "в другое место, чем возврат (в живой копии — на крышу). Нарушители");
    }

    @Test
    void settlementHome_IsMedian_NotFirstSpirit() {
        String text = read(SourceFiles.path("neoforge/board/SettlementHome.java"));
        String stand = read(SourceFiles.path("neoforge/board/HomeStand.java"));

        assertFalse(
                text.contains("findFirst"),
                "SettlementHome.center не берёт первый дом: от порядка духов менялись цель dig_out и сцена "
                        + "GameTest. Как надо: SettlementPlot.center по всем домам");
        assertTrue(
                text.contains("SettlementPlot.center("),
                "SettlementHome.center обязан считать середину домов: одна точка не зависит от порядка духов");
        assertTrue(
                stand.contains("SettlementHome.homes("),
                "HomeStand.of обязан учитывать spirits: при заданных домах домашняя стоянка — их середина, "
                        + "а не новая клетка у якоря этапа");
    }

    @Test
    void homePoint_NoHomeAndNoStage_IsAbsentNotOrigin() {
        String home = read(SourceFiles.path("neoforge/board/SettlementHome.java"));
        String point = read(SourceFiles.path("neoforge/board/HomePoint.java"));
        assertFalse(
                home.contains("new BlockCoord(0, 64, 0)"),
                "Поселение без домов — это отсутствие точки, а не (0,64,0): выдуманная координата уводит "
                        + "dig_out и снабжение в пустой угол мира. Как надо: SettlementHome.center возвращает "
                        + "Optional.empty, а вызывающий пропускает работу без поселения");
        assertFalse(
                point.contains("SettlementHome.of("),
                "HomePoint не подставляет выдуманный дом в поселении без домов и этапа: цель считается "
                        + "известной только когда есть дом духа или живой этап. Как надо: Optional.empty и "
                        + "явный пропуск у вызывающего");
        assertTrue(
                point.contains("Optional<BlockCoord> of(ServerLevel level, List<SpiritEntity> spirits)"),
                "Отсутствие дома обязано быть видно в типе: без Optional вызывающий снова получит координату "
                        + "из воздуха");
    }

    @Test
    void homeRadius_ModCopies_UseSingleCoreConstant() {
        String goal = read(SourceFiles.path("core/movement/DigOutGoal.java"));
        assertTrue(
                goal.contains("HOME_RADIUS = 2"),
                "Радиус дома живёт в core одной константой: две копии в DigOutScript и SurfaceRoute уже "
                        + "расходились по смыслу цели выхода");
        for (String file : List.of("neoforge/DigOutScript.java", "neoforge/movement/SurfaceRoute.java")) {
            String text = read(SourceFiles.path(file));
            assertFalse(
                    text.contains("HOME_RADIUS ="),
                    file + " не объявляет свой радиус дома: значение берётся из DigOutGoal.HOME_RADIUS, "
                            + "иначе копии снова разойдутся");
            assertTrue(
                    text.contains("DigOutGoal.HOME_RADIUS"),
                    file + " обязан брать радиус дома из core: одна константа на выход и спуск");
        }
    }

    @Test
    void digOutRoute_FromShaft_GoesHomeNotAnySkyCell() {
        String route = read(SourceFiles.path("neoforge/DigOutRoute.java"));
        String surface = read(SourceFiles.path("neoforge/movement/SurfaceRoute.java"));

        assertTrue(
                route.contains("SurfaceRoute.begin("),
                "Маршрут dig_out ищет поверхность у дома, а не любое небо: иначе дух после выхода из кармана "
                        + "идёт к краю площадки и падает в пустоту (падающий шаг DigOutGameTests)");
        assertTrue(
                surface.contains("SurfaceEscape.toHome(") && surface.contains("SurfaceGoal.home("),
                "Признак поверхности у дома живёт в SurfaceGoal.home: любой getHeight/open без x/z снова "
                        + "уведёт маршрут на чужое небо");
    }

    private static List<String> heightLifts(Map<String, String> sources) {
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            for (String lift : HEIGHT_LIFTS) {
                if (text.contains(lift)) {
                    offenders.add(file + ": " + lift);
                }
            }
        });
        return offenders;
    }

    private static boolean supportBeforePlace(String text) {
        int support = text.indexOf(SUPPORT);
        int place = text.indexOf(PLACE);
        return support >= 0 && place > support;
    }

    private static boolean trappedCheck(String text) {
        return text.contains(TRAPPED);
    }

    private static boolean escapeAfterTrapped(String text) {
        int check = text.indexOf("BuildPit.needsEscape(");
        int escape = text.indexOf("pit.escape(");
        return check >= 0 && escape > check;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал " + file + ": " + error.getMessage(), error);
        }
    }
}

package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageSiteTests {
    static final Path ROOT = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void hut_Scheme_InsideHutPlot() throws IOException {
        Schematic hut = SchematicTextParser.parse(Files.readString(ROOT.resolve("hut_1.txt")), "hut_1")
                .schematic();
        BlockCoord house =
                new BlockCoord(SITE.hut().x(), SITE.anchor().y(), SITE.hut().z());
        List<BlockCoord> corners = new ArrayList<>(
                List.of(house, new BlockCoord(house.x() + hut.sizeX() - 1, house.y(), house.z() + hut.sizeZ() - 1)));
        for (BlockCoord corner : corners) {
            assertTrue(
                    SITE.hut().covers(corner.x(), corner.z()),
                    "Клетка " + corner + " хижины вне её места в плане " + SITE.hut()
                            + ": хижина строится только на своей площадке"
                            + " Иначе тест hut_Scheme_InsideHutPlot перестанет ловить эту поломку. При смене hut_Scheme_InsideHutPlot проверь это утверждение.");
        }
        assertEquals(
                List.of(SITE.hut().width(), SITE.hut().depth()),
                List.of(hut.sizeX(), hut.sizeZ()),
                "Габарит схемы hut_1 обязан совпадать с участком: схема — коробка участка 16×16"
                        + " При смене hut_Scheme_InsideHutPlot проверь это утверждение.");
    }

    @Test
    void yard_SchemeCells_InsideYardOffWorkPoint() {
        List<BlockCoord> cells = new ArrayList<>();
        DataTables.INSTANCE
                .presets()
                .current()
                .byId(YardScheme.YARD)
                .orElseThrow()
                .schematic()
                .entries(YardScheme.origin(SITE))
                .forEach(entry -> cells.add(entry.pos()));
        for (BlockCoord cell : cells) {
            assertTrue(
                    SITE.yardColumn(cell.x(), cell.z()),
                    "Клетка двора " + cell + " вне места двора " + SITE.yard() + ": двор — площадка этапа 1"
                            + " Иначе тест yard_SchemeCells_InsideYardOffWorkPoint перестанет ловить эту поломку. При смене yard_SchemeCells_InsideYardOffWorkPoint проверь это утверждение.");
        }
        assertTrue(
                !cells.contains(SITE.workYard()),
                "Точка двора, где дух крафтит и берёт из сундуков, свободна: схема двора её не занимает"
                        + " Иначе тест yard_SchemeCells_InsideYardOffWorkPoint перестанет ловить эту поломку. При смене yard_SchemeCells_InsideYardOffWorkPoint проверь это утверждение.");
    }

    @Test
    void planCell_HutBlocks_ProtectedFromSpiritDismantle() throws IOException {
        Schematic hut = SchematicTextParser.parse(Files.readString(ROOT.resolve("hut_1.txt")), "hut_1")
                .schematic();
        for (Schematic.PlacedEntry entry : hut.entries(SITE.houseOrigin())) {
            assertTrue(
                    SITE.planCell(entry.pos().x(), entry.pos().y(), entry.pos().z()),
                    "Блок хижины " + entry.pos() + " обязан быть клеткой плана: иначе разбор своего столба "
                            + "и рубка принимают стену за обломок и снимают её (хижина 97%)"
                            + " При смене planCell_HutBlocks_ProtectedFromSpiritDismantle проверь это утверждение.");
        }
    }

    @Test
    void mine_ColumnAndPassage_InsideMinePlot() {
        BlockCoord entrance = SITE.mineEntrance();
        assertTrue(
                SITE.mineColumn(entrance.x(), entrance.z()) && SITE.mine().covers(entrance.x(), entrance.z()),
                "Вход в шахту " + entrance + " обязан лежать в колонке спуска внутри её места " + SITE.mine()
                        + " Иначе тест mine_ColumnAndPassage_InsideMinePlot перестанет ловить эту поломку. При смене mine_ColumnAndPassage_InsideMinePlot проверь это утверждение.");
        assertTrue(
                SITE.minePassage(entrance.x() + 1, entrance.z() + 2)
                        && !SITE.minePassage(entrance.x(), entrance.z() + 2),
                "Проход спуска — средняя клетка колонки шириной 3: схема mine_descent_v1 повёрнута по часовой"
                        + " Иначе тест mine_ColumnAndPassage_InsideMinePlot перестанет ловить эту поломку. При смене mine_ColumnAndPassage_InsideMinePlot проверь это утверждение.");
        assertFalse(
                SITE.levelColumn(entrance.x(), entrance.z()),
                "Колонка спуска не выравнивается: срезка завалит или вскроет спуск"
                        + " Иначе тест mine_ColumnAndPassage_InsideMinePlot перестанет ловить эту поломку. При смене mine_ColumnAndPassage_InsideMinePlot проверь это утверждение.");
    }

    @Test
    void ring_EnclosesStageAndAnchor_PadsOffTheFence() {
        List<Placement> stage = List.of(SITE.hut(), SITE.yard(), SITE.mine(), SITE.orchard());
        for (Placement place : stage) {
            for (ColumnMask.Cell cell : place.mask().cells()) {
                assertTrue(
                        SITE.inside(cell.x(), cell.z()) && !SITE.ringPost(cell.x(), cell.z()),
                        "Клетка " + cell + " места '" + place.id() + "' вне ограды этапа или под её столбом: "
                                + "всё этапа 1 стоит внутри ограды"
                                + " Иначе тест ring_EnclosesStageAndAnchor_PadsOffTheFence перестанет ловить эту поломку. При смене ring_EnclosesStageAndAnchor_PadsOffTheFence проверь это утверждение.");
            }
        }
        assertTrue(
                SITE.inside(SITE.anchor().x(), SITE.anchor().z()),
                "Дом духов (якорь) обязан быть внутри ограды этапа"
                        + " Иначе тест ring_EnclosesStageAndAnchor_PadsOffTheFence перестанет ловить эту поломку. При смене ring_EnclosesStageAndAnchor_PadsOffTheFence проверь это утверждение.");
        assertFalse(
                SITE.ring().gates().isEmpty(),
                "Ограда этапа без калиток: духи не выйдут за землёй и деревом"
                        + " Иначе тест ring_EnclosesStageAndAnchor_PadsOffTheFence перестанет ловить эту поломку. При смене ring_EnclosesStageAndAnchor_PadsOffTheFence проверь это утверждение.");
    }

    @Test
    void level_PadsAndStreetsBetween_OrchardLeveled() {
        for (Placement pad : List.of(SITE.hut(), SITE.yard(), SITE.mine(), SITE.orchard())) {
            for (ColumnMask.Cell cell : pad.mask().cells()) {
                assertTrue(
                        SITE.level().contains(cell.x(), cell.z()),
                        "Клетка " + cell + " площадки '" + pad.id() + "' не выравнивается: постройка встанет на уступ"
                                + " Иначе тест level_PadsAndStreetsBetween_OrchardLeveled перестанет ловить эту поломку. При смене level_PadsAndStreetsBetween_OrchardLeveled проверь это утверждение.");
            }
        }
        for (ColumnMask.Cell cell : SITE.orchard().mask().cells()) {
            assertTrue(
                    SITE.level().contains(cell.x(), cell.z()),
                    "Сад " + cell + " не выравнивается: схема orchard_1 ставится на ровную площадку, иначе "
                            + "тропинки и саженцы повиснут или утонут"
                            + " При смене level_PadsAndStreetsBetween_OrchardLeveled проверь это утверждение.");
        }
        assertTrue(
                SITE.level().count() < SITE.zone().count(),
                "Площадки этапа — часть зоны, а не вся зона: ровность считается по ограде и участкам "
                        + "(решение владельца 09.11), а не только по площадкам"
                        + " Иначе тест level_PadsAndStreetsBetween_OrchardLeveled перестанет ловить эту поломку. При смене level_PadsAndStreetsBetween_OrchardLeveled проверь это утверждение.");
    }

    @Test
    void level_FenceLine_PostsAndStripLeveled() {
        for (ColumnMask.Cell post : SITE.ring().posts()) {
            assertTrue(
                    SITE.level().contains(post.x(), post.z()) || SITE.mineColumn(post.x(), post.z()),
                    "Столб ограды " + post + " не на площадке выравнивания: забор встанет на разных высотах (INC-91)"
                            + " Иначе тест level_FenceLine_PostsAndStripLeveled перестанет ловить эту поломку. При смене level_FenceLine_PostsAndStripLeveled проверь это утверждение.");
        }
    }

    @Test
    void gatherForbidden_MineColumn_ShallowKeptDeepAllowed() {
        BlockCoord entrance = SITE.mineEntrance();
        BlockCoord wall = new BlockCoord(entrance.x(), entrance.y() - 2, entrance.z() + 3);
        BlockCoord deep =
                new BlockCoord(entrance.x(), SITE.anchor().y() - StageSite.MINE_SHALLOW - 1, entrance.z() + 3);
        assertTrue(
                SITE.gatherForbidden(wall, 2),
                "Стенки спуска у поверхности не добываются: добыча булыжника разбирала спуск, и его чинили по кругу"
                        + " Иначе тест gatherForbidden_MineColumn_ShallowKeptDeepAllowed перестанет ловить эту поломку. При смене gatherForbidden_MineColumn_ShallowKeptDeepAllowed проверь это утверждение.");
        assertFalse(
                SITE.gatherForbidden(deep, StageSite.MINE_SHALLOW + 1),
                "Глубже спуска колонна шахты — законный карьер: булыжник поселение берёт только в шахте"
                        + " Иначе тест gatherForbidden_MineColumn_ShallowKeptDeepAllowed перестанет ловить эту поломку. При смене gatherForbidden_MineColumn_ShallowKeptDeepAllowed проверь это утверждение.");
    }

    @Test
    void gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed() {
        BlockCoord inside = new BlockCoord(
                SITE.orchard().x(), TownFixtures.LEVEL - 1, SITE.orchard().z());
        BlockCoord far = new BlockCoord(
                SITE.enclosure().minX() - StageSite.CAVE_MARGIN - 5,
                TownFixtures.LEVEL - 9,
                SITE.anchor().z());
        assertTrue(
                SITE.gatherForbidden(inside, 1),
                "Земля внутри ограды этапа — не карьер: добыча здесь роет ямы на участке"
                        + " Иначе тест gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed перестанет ловить эту поломку. При смене gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed проверь это утверждение.");
        assertFalse(
                SITE.gatherForbidden(far, 9),
                "Пещера далеко за оградой не под поселением: запрет там оставил бы духов без камня"
                        + " Иначе тест gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed перестанет ловить эту поломку. При смене gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed проверь это утверждение.");
        assertFalse(
                StageStands.fillStandAllowed(SITE, SITE.workYard(), false),
                "Подсыпка со стоянки на площадке под крышей недопустима: дух замуруется в площадке"
                        + " Иначе тест gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed перестанет ловить эту поломку. При смене gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed проверь это утверждение.");
    }

    @Test
    void planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth() {
        MinePlot plot = SITE.minePlot();
        BlockCoord wall = new BlockCoord(plot.x1() - 1, SITE.anchor().y() - 20, plot.z1() + 20);

        assertTrue(
                SITE.minePlot().covers(wall.x() + 1, wall.z()),
                "Предусловие: стенка " + wall + " стоит вплотную к коридору спуска " + plot
                        + " Иначе тест planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth перестанет ловить эту поломку. При смене planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth проверь это утверждение.");
        assertTrue(
                SITE.planCell(wall.x(), wall.y(), wall.z()),
                "Свои стенки и факелы спуска входят в план на любой глубине: факел у " + wall + " вне клетки плана "
                        + "попадал в чёрный список защиты (observe8: wall_torch держал шахту на 75%)"
                        + " Иначе тест planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth перестанет ловить эту поломку. При смене planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth проверь это утверждение.");
    }

    @Test
    void complete_StoredPlanWithoutOrchard_IsIncompleteAndGetsReplanned() {
        TownPlan full = TownFixtures.flatPlan();
        TownPlan old = new TownPlan(
                full.anchor(),
                full.region(),
                full.streets(),
                full.placements().stream()
                        .filter(place -> !StageSites.ORCHARD.equals(place.id()))
                        .toList(),
                full.fence(),
                full.unplaced());

        assertTrue(
                StageSites.complete(full),
                "свежий план содержит все места этапа 1"
                        + " Иначе тест complete_StoredPlanWithoutOrchard_IsIncompleteAndGetsReplanned перестанет ловить эту поломку. При смене complete_StoredPlanWithoutOrchard_IsIncompleteAndGetsReplanned проверь это утверждение.");
        assertFalse(
                StageSites.complete(old),
                "план мира без сада (старая «роща») не годится и размечается заново"
                        + " Иначе тест complete_StoredPlanWithoutOrchard_IsIncompleteAndGetsReplanned перестанет ловить эту поломку. При смене complete_StoredPlanWithoutOrchard_IsIncompleteAndGetsReplanned проверь это утверждение.");
    }

    @Test
    void protectionCell_StreetCell_True() {
        StageSite site = TownFixtures.flatSite();
        TownPlan plan = TownFixtures.flatPlan();
        ColumnMask.Cell street =
                plan
                        .streets()
                        .main()
                        .union(plan.streets().side())
                        .union(plan.streets().ring())
                        .cells()
                        .stream()
                        .filter(cell -> !site.planCell(cell.x(), site.anchor().y() - 1, cell.z()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("В плане нет улицы вне площадок построек"));
        int y = site.anchor().y() - 1;

        assertFalse(
                site.planCell(street.x(), y, street.z()),
                "Улица — не площадка постройки: planCell её не покрывает, поэтому защита сбора отдельная"
                        + " Иначе тест protectionCell_StreetCell_True перестанет ловить эту поломку. При смене protectionCell_StreetCell_True проверь это утверждение.");
        assertTrue(
                site.streetColumn(street.x(), street.z()),
                "Любая клетка улицы плана (главной, боковой, кольца) обязана быть узнаваема: иначе дух снова "
                        + "выкопает покрытие улицы под материал"
                        + " При смене protectionCell_StreetCell_True проверь это утверждение.");
        assertTrue(
                site.protectionCell(street.x(), y, street.z()),
                "observe22: сбор земли у поселения выкопал блоки улиц снаружи ограды (forbiddenBySettlement "
                        + "ловит только природные блоки внутри) — защита сбора обязана покрывать улицы"
                        + " Иначе тест protectionCell_StreetCell_True перестанет ловить эту поломку. При смене protectionCell_StreetCell_True проверь это утверждение.");
    }

    @Test
    void protectionCell_FreeGroundOutsidePlan_False() {
        StageSite site = TownFixtures.flatSite();

        assertFalse(
                site.protectionCell(
                        site.anchor().x() + 1000,
                        site.anchor().y(),
                        site.anchor().z() + 1000),
                "За пределами плана добыча остаётся разрешённой: иначе духи не добудут землю вовсе"
                        + " При смене protectionCell_FreeGroundOutsidePlan_False проверь это утверждение.");
    }

    @Test
    void planCell_TreeAboveMineLot_NotPlan() {
        int x = SITE.mine().x() + SITE.mine().width() / 2;
        int z = SITE.mine().z() + SITE.mine().depth() / 2;
        int above = SITE.anchor().y() + StageSite.BUILDING_HEIGHT + 10;

        assertTrue(
                SITE.planCell(x, SITE.anchor().y(), z),
                "Колонка лота шахты у земли — план: спуск и его вход принадлежат постройке"
                        + " Иначе тест planCell_TreeAboveMineLot_NotPlan перестанет ловить эту поломку. При смене planCell_TreeAboveMineLot_NotPlan проверь это утверждение.");
        assertFalse(
                SITE.planCell(x, above, z),
                "Дерево над лотом шахты (y=" + above + ") — не клетка плана: иначе рубка отказывается целиком "
                        + "(observe24: бревно y=97 над лотом шахты, 4 отказа, дерево не срублено)"
                        + " При смене planCell_TreeAboveMineLot_NotPlan проверь это утверждение.");
    }
}

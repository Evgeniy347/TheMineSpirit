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
    private static final Path ROOT = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void hut_Scheme_InsideHutPlot() throws IOException {
        Schematic hut = SchematicTextParser.parse(Files.readString(ROOT.resolve("hut_1.txt")), "hut_1")
                .schematic();
        BlockCoord house = SITE.houseOrigin();
        List<BlockCoord> corners = new ArrayList<>(
                List.of(house, new BlockCoord(house.x() + hut.sizeX() - 1, house.y(), house.z() + hut.sizeZ() - 1)));
        for (BlockCoord corner : corners) {
            assertTrue(
                    SITE.hut().covers(corner.x(), corner.z()),
                    "Клетка " + corner + " хижины вне её места в плане " + SITE.hut()
                            + ": хижина строится только на своей площадке");
        }
        assertEquals(
                List.of(StageSite.HOUSE_WIDTH, StageSite.HOUSE_DEPTH),
                List.of(hut.sizeX(), hut.sizeZ()),
                "Габарит хижины в месте этапа обязан совпадать со схемой hut_1, иначе аудит хижины промахнётся");
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
                    "Клетка двора " + cell + " вне места двора " + SITE.yard() + ": двор — площадка этапа 1");
        }
        assertTrue(
                !cells.contains(SITE.workYard()),
                "Точка двора, где дух крафтит и берёт из сундуков, свободна: схема двора её не занимает");
        assertTrue(
                YardScheme.slots(SITE).size() >= 2,
                "Ряд сундуков двора держит запас под рост склада (решение владельца 25.10), а мест "
                        + YardScheme.slots(SITE).size());
    }

    @Test
    void planCell_HutBlocks_ProtectedFromSpiritDismantle() throws IOException {
        Schematic hut = SchematicTextParser.parse(Files.readString(ROOT.resolve("hut_1.txt")), "hut_1")
                .schematic();
        for (Schematic.PlacedEntry entry : hut.entries(SITE.houseOrigin())) {
            assertTrue(
                    SITE.planCell(entry.pos().x(), entry.pos().y(), entry.pos().z()),
                    "Блок хижины " + entry.pos() + " обязан быть клеткой плана: иначе разбор своего столба "
                            + "и рубка принимают стену за обломок и снимают её (хижина 97%)");
        }
    }

    @Test
    void mine_ColumnAndPassage_InsideMinePlot() {
        BlockCoord entrance = SITE.mineEntrance();
        assertTrue(
                SITE.mineColumn(entrance.x(), entrance.z()) && SITE.mine().covers(entrance.x(), entrance.z()),
                "Вход в шахту " + entrance + " обязан лежать в колонке спуска внутри её места " + SITE.mine());
        assertTrue(
                SITE.minePassage(entrance.x() + 1, entrance.z() + 2)
                        && !SITE.minePassage(entrance.x(), entrance.z() + 2),
                "Проход спуска — средняя клетка колонки шириной 3: схема mine_descent_v1 повёрнута по часовой");
        assertFalse(
                SITE.levelColumn(entrance.x(), entrance.z()),
                "Колонка спуска не выравнивается: срезка завалит или вскроет спуск");
    }

    @Test
    void ring_EnclosesStageAndAnchor_PadsOffTheFence() {
        List<Placement> stage = List.of(SITE.hut(), SITE.yard(), SITE.mine(), SITE.orchard());
        for (Placement place : stage) {
            for (ColumnMask.Cell cell : place.mask().cells()) {
                assertTrue(
                        SITE.inside(cell.x(), cell.z()) && !SITE.ringPost(cell.x(), cell.z()),
                        "Клетка " + cell + " места '" + place.id() + "' вне ограды этапа или под её столбом: "
                                + "всё этапа 1 стоит внутри ограды");
            }
        }
        assertTrue(
                SITE.inside(SITE.anchor().x(), SITE.anchor().z()), "Дом духов (якорь) обязан быть внутри ограды этапа");
        assertFalse(SITE.ring().gates().isEmpty(), "Ограда этапа без калиток: духи не выйдут за землёй и деревом");
    }

    @Test
    void level_PadsAndStreetsBetween_OrchardLeveled() {
        for (Placement pad : List.of(SITE.hut(), SITE.yard(), SITE.mine(), SITE.orchard())) {
            for (ColumnMask.Cell cell : pad.mask().cells()) {
                assertTrue(
                        SITE.level().contains(cell.x(), cell.z()),
                        "Клетка " + cell + " площадки '" + pad.id() + "' не выравнивается: постройка встанет на уступ");
            }
        }
        for (ColumnMask.Cell cell : SITE.orchard().mask().cells()) {
            assertTrue(
                    SITE.level().contains(cell.x(), cell.z()),
                    "Сад " + cell + " не выравнивается: схема orchard_1 ставится на ровную площадку, иначе "
                            + "тропинки и саженцы повиснут или утонут");
        }
        assertTrue(
                SITE.level().count() < SITE.enclosure().count() / 2,
                "Выравнивается " + SITE.level().count() + " колонок из "
                        + SITE.enclosure().count()
                        + " огороженных: выравнивать весь участок запрещено (решение владельца 25.10)");
    }

    @Test
    void level_FenceLine_PostsAndStripLeveled() {
        for (ColumnMask.Cell post : SITE.ring().posts()) {
            assertTrue(
                    SITE.level().contains(post.x(), post.z()) || SITE.mineColumn(post.x(), post.z()),
                    "Столб ограды " + post + " не на площадке выравнивания: забор встанет на разных высотах (INC-91)");
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
                "Стенки спуска у поверхности не добываются: добыча булыжника разбирала спуск, и его чинили по кругу");
        assertFalse(
                SITE.gatherForbidden(deep, StageSite.MINE_SHALLOW + 1),
                "Глубже спуска колонна шахты — законный карьер: булыжник поселение берёт только в шахте");
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
                "Земля внутри ограды этапа — не карьер: добыча здесь роет ямы на участке");
        assertFalse(
                SITE.gatherForbidden(far, 9),
                "Пещера далеко за оградой не под поселением: запрет там оставил бы духов без камня");
        assertFalse(
                SITE.fillStandAllowed(SITE.workYard(), false),
                "Подсыпка со стоянки на площадке под крышей недопустима: дух замуруется в площадке");
    }

    @Test
    void planCell_DeepMineWallAndTorch_IsPlanAtAnyDepth() {
        MinePlot plot = SITE.minePlot();
        BlockCoord wall = new BlockCoord(plot.x1() - 1, SITE.anchor().y() - 20, plot.z1() + 20);

        assertTrue(
                SITE.minePlot().covers(wall.x() + 1, wall.z()),
                "Предусловие: стенка " + wall + " стоит вплотную к коридору спуска " + plot);
        assertTrue(
                SITE.planCell(wall.x(), wall.y(), wall.z()),
                "Свои стенки и факелы спуска входят в план на любой глубине: факел у " + wall + " вне клетки плана "
                        + "попадал в чёрный список защиты (observe8: wall_torch держал шахту на 75%)");
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

        assertTrue(StageSites.complete(full), "свежий план содержит все места этапа 1");
        assertFalse(StageSites.complete(old), "план мира без сада (старая «роща») не годится и размечается заново");
    }

    @Test
    void gatherProtected_StreetCell_True() {
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
                "Улица — не площадка постройки: planCell её не покрывает, поэтому защита сбора отдельная");
        assertTrue(
                site.streetColumn(street.x(), street.z()),
                "Любая клетка улицы плана (главной, боковой, кольца) обязана быть узнаваема: иначе дух снова "
                        + "выкопает покрытие улицы под материал");
        assertTrue(
                site.gatherProtected(street.x(), y, street.z()),
                "observe22: сбор земли у поселения выкопал блоки улиц снаружи ограды (forbiddenBySettlement "
                        + "ловит только природные блоки внутри) — защита сбора обязана покрывать улицы");
    }

    @Test
    void gatherProtected_FreeGroundOutsidePlan_False() {
        StageSite site = TownFixtures.flatSite();

        assertFalse(
                site.gatherProtected(
                        site.anchor().x() + 1000,
                        site.anchor().y(),
                        site.anchor().z() + 1000),
                "За пределами плана добыча остаётся разрешённой: иначе духи не добудут землю вовсе");
    }

    @Test
    void planCell_TreeAboveMineLot_NotPlan() {
        int x = SITE.mine().x() + SITE.mine().width() / 2;
        int z = SITE.mine().z() + SITE.mine().depth() / 2;
        int above = SITE.anchor().y() + StageSite.BUILDING_HEIGHT + 10;

        assertTrue(
                SITE.planCell(x, SITE.anchor().y(), z),
                "Колонка лота шахты у земли — план: спуск и его вход принадлежат постройке");
        assertFalse(
                SITE.planCell(x, above, z),
                "Дерево над лотом шахты (y=" + above + ") — не клетка плана: иначе рубка отказывается целиком "
                        + "(observe24: бревно y=97 над лотом шахты, 4 отказа, дерево не срублено)");
    }

    @Test
    void planCell_RingPostColumn_OnlyAtFenceHeight() {
        ColumnMask.Cell post = SITE.ring().posts().iterator().next();
        int above = SITE.anchor().y() + StageSite.BUILDING_HEIGHT + 10;

        assertTrue(
                SITE.planCell(post.x(), SITE.anchor().y(), post.z()),
                "Столб ограды у земли — клетка плана: его нельзя выломать случайной уборкой");
        assertFalse(
                SITE.planCell(post.x(), above, post.z()),
                "Столб ограды не занимает бесконечную колонку: дерево над ним рубится, а не считается планом");
        assertFalse(
                SITE.planCell(post.x(), SITE.anchor().y() - 10, post.z()),
                "Под землёй столба ограды нет: глубокую клетку той же колонки план не защищает");
        assertEquals(
                PlanBuilding.NONE,
                SITE.planBuilding(post.x(), above, post.z()),
                "Имя постройки над оградой не выдаётся: иначе защита считает дерево частью забора");
    }

    @Test
    void planCell_LiveObserve24TreeAboveMine_NotPlan() throws IOException {
        String text = Files.readString(Path.of("src/test/resources/live/observe24/town-plan.txt"))
                .replace("\r\n", "\n");
        TownPlan plan = TownPlanCodec.decode(text);
        StageSite site = StageSites.of(plan, 1, plan.anchor().y(), TownRules.load());

        assertTrue(
                site.mine().covers(-163, -507),
                "Живой план observe24: дерево (-163,85,-507) стоит над лотом шахты " + site.mine());
        assertFalse(
                site.planCell(-163, 97, -507),
                "Живой случай observe24: бревно y=97 над лотом шахты — не план, дерево рубится целиком");
        assertTrue(
                site.planCell(-163, plan.anchor().y(), -507),
                "Предусловие: у земли та же колонка остаётся планом шахты");
    }
}

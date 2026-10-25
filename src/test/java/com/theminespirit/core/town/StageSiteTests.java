package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageSiteTests {
    private static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void hut_SchemeAndPalisade_InsideHutPlot() throws IOException {
        Schematic hut = SchematicTextParser.parse(Files.readString(ROOT.resolve("hut_1.txt")), "hut_1")
                .schematic();
        BlockCoord house = SITE.houseOrigin();
        List<BlockCoord> corners = new ArrayList<>(
                List.of(house, new BlockCoord(house.x() + hut.sizeX() - 1, house.y(), house.z() + hut.sizeZ() - 1)));
        StagePlans.palisade(SITE).forEach(block -> corners.add(block.at()));
        for (BlockCoord corner : corners) {
            assertTrue(
                    SITE.hut().covers(corner.x(), corner.z()),
                    "Клетка " + corner + " хижины или палисадника вне её места в плане " + SITE.hut()
                            + ": хижина строится только на своей площадке");
        }
        assertEquals(
                List.of(StageSite.HOUSE_WIDTH, StageSite.HOUSE_DEPTH),
                List.of(hut.sizeX(), hut.sizeZ()),
                "Габарит хижины в месте этапа обязан совпадать со схемой hut_1, иначе аудит хижины промахнётся");
    }

    @Test
    void yard_ChestSlotsTableFurnace_InsideYardAndDistinct() {
        List<BlockCoord> cells = new ArrayList<>(List.of(SITE.craftingTable(), SITE.furnace(), SITE.workYard()));
        SITE.chestSlots().forEach(cells::addAll);
        for (BlockCoord cell : cells) {
            assertTrue(
                    SITE.yardColumn(cell.x(), cell.z()),
                    "Клетка двора " + cell + " вне места двора " + SITE.yard() + ": двор — площадка этапа 1");
        }
        assertEquals(
                cells.size(),
                cells.stream().distinct().count(),
                "Сундуки, верстак, печь и точка двора не делят клетки: иначе второй блок не поставить");
        assertTrue(
                SITE.chestSlots().size() >= 2,
                "Ряд сундуков двора держит запас под рост склада (решение владельца 25.10), а мест "
                        + SITE.chestSlots().size());
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
        List<Placement> stage = List.of(SITE.hut(), SITE.yard(), SITE.mine(), SITE.grove());
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
    void level_PadsAndStreetsBetween_GroveNotLeveled() {
        for (Placement pad : List.of(SITE.hut(), SITE.yard(), SITE.mine())) {
            for (ColumnMask.Cell cell : pad.mask().cells()) {
                assertTrue(
                        SITE.level().contains(cell.x(), cell.z()),
                        "Клетка " + cell + " площадки '" + pad.id() + "' не выравнивается: постройка встанет на уступ");
            }
        }
        for (ColumnMask.Cell cell : SITE.grove().mask().cells()) {
            assertFalse(
                    SITE.level().contains(cell.x(), cell.z()),
                    "Роща " + cell + " выравнивается: выравнивание — только площадки построек и улицы между ними");
        }
        assertTrue(
                SITE.level().count() < SITE.enclosure().count() / 2,
                "Выравнивается " + SITE.level().count() + " колонок из "
                        + SITE.enclosure().count()
                        + " огороженных: выравнивать весь участок запрещено (решение владельца 25.10)");
    }

    @Test
    void gatherForbidden_InsideFenceAndDeepNearby_ForbiddenFarCaveAllowed() {
        BlockCoord inside = new BlockCoord(
                SITE.grove().x(), TownFixtures.LEVEL - 1, SITE.grove().z());
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
}

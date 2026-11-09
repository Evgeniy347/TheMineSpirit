package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicUpgrade;
import com.theminespirit.core.forestry.ChopPlan;
import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotMechanicsTests {
    private static final StageSite SITE = TownFixtures.flatSite();
    private static final BlockCoord HOUSE = SITE.houseOrigin();
    private static final BlockCoord MINE = mineCell(SITE.anchor().y() - 10);
    private static final BlockCoord STREET = streetCell(SITE.anchor().y());
    private static final BlockCoord OUTSIDE = new BlockCoord(
            SITE.anchor().x() + 400, SITE.anchor().y(), SITE.anchor().z());

    @Test
    void mineVein_PartOfVeinIsBuilding_DigsEverythingExceptProtected() {
        List<BlockCoord> vein = List.of(
                new BlockCoord(HOUSE.x(), HOUSE.y(), HOUSE.z()),
                new BlockCoord(HOUSE.x() + 1, HOUSE.y(), HOUSE.z()),
                new BlockCoord(OUTSIDE.x(), OUTSIDE.y() - 2, OUTSIDE.z()),
                new BlockCoord(OUTSIDE.x() + 1, OUTSIDE.y() - 2, OUTSIDE.z()));

        List<BlockCoord> mined = harvestable(vein, PlotProtection.Action.HARVEST);

        assertEquals(
                List.of(
                        new BlockCoord(OUTSIDE.x(), OUTSIDE.y() - 2, OUTSIDE.z()),
                        new BlockCoord(OUTSIDE.x() + 1, OUTSIDE.y() - 2, OUTSIDE.z())),
                mined,
                "mine_vein добывает жилу целиком, кроме клеток постройки: защищённая руда остаётся в стене, "
                        + "а не выедается вместе с домом");
    }

    @Test
    void tree_ProtectedLogs_ChoppedAroundAndNotLeftUnfinished() {
        Tree tree = new Tree(
                OUTSIDE,
                List.of(
                        OUTSIDE,
                        new BlockCoord(OUTSIDE.x(), OUTSIDE.y() + 1, OUTSIDE.z()),
                        new BlockCoord(OUTSIDE.x(), OUTSIDE.y() + 2, OUTSIDE.z()),
                        new BlockCoord(HOUSE.x(), HOUSE.y() + 1, HOUSE.z())));

        ChopPlan plan = ChopPlan.of(tree, OUTSIDE, cell -> protectedByPlan(cell, PlotProtection.Action.CHOP));

        assertFalse(
                plan.order().contains(new BlockCoord(HOUSE.x(), HOUSE.y() + 1, HOUSE.z())),
                "Бревно-постройка не попадает в план рубки: иначе дух рубит дерево сквозь дом");
        assertEquals(
                List.of(
                        new BlockCoord(OUTSIDE.x(), OUTSIDE.y() + 2, OUTSIDE.z()),
                        new BlockCoord(OUTSIDE.x(), OUTSIDE.y() + 1, OUTSIDE.z()),
                        OUTSIDE),
                plan.order(),
                "Остальное дерево снимается целиком, включая нижнее бревно, а не бросается из-за одного "
                        + "защищённого ствола");
    }

    @Test
    void dirtGatherAndQuarry_PlanCells_AreNotTouched() {
        List<BlockCoord> targets = List.of(HOUSE, MINE, STREET, OUTSIDE);

        assertEquals(
                List.of(OUTSIDE),
                harvestable(targets, PlotProtection.Action.HARVEST),
                "gather dirt не берёт грунт с дома, шахты и улицы: это не ресурс, а постройка");
        assertEquals(
                List.of(OUTSIDE),
                harvestable(targets, PlotProtection.Action.DIG),
                "карьер не раскапывает клетки постройки: иначе под домом появляется яма");
    }

    @Test
    void fillAndPlow_PlanCells_AreNotFilled() {
        List<BlockCoord> targets = List.of(HOUSE, MINE, STREET, OUTSIDE);

        assertEquals(
                List.of(OUTSIDE),
                placeable(targets),
                "засыпка и пахота не ставят блоки и пашню в клетки постройки: это заменяет стену или пол двора");
    }

    @Test
    void clearFloatingAndStray_PlanCells_AreKept() {
        List<BlockCoord> targets = List.of(HOUSE, MINE, STREET, OUTSIDE);

        assertEquals(
                List.of(OUTSIDE),
                removable(targets, PlotProtection.Ground.NONE, PlanBuilding.NONE),
                "расчистка и уборка висящего не снимают блоки плана без основания");
        assertEquals(
                List.of(HOUSE, OUTSIDE),
                removable(targets, PlotProtection.Ground.PLAN_DEMOLISH, PlanBuilding.HOUSE),
                "снос дома по плану снимает его клетки, но не шахту и не улицу: чужая постройка остаётся");
    }

    @Test
    void navigationDigBridgePillar_PlanCells_AreAvoided() {
        List<BlockCoord> targets = List.of(HOUSE, MINE, STREET, OUTSIDE);

        assertEquals(
                List.of(OUTSIDE),
                removable(targets, PlotProtection.Ground.NONE, PlanBuilding.NONE),
                "проход не прокапывает постройку: dig_out и навигация обходят её или отказываются");
        assertEquals(
                List.of(OUTSIDE),
                placeable(targets),
                "мост и столб не ставятся в клетку постройки: иначе опора проходит сквозь стену");
    }

    @Test
    void upgrade_OldSchemeExtras_RemovedNewSchemeKept() {
        BlockCoord origin = HOUSE;
        Schematic previous = new Schematic(
                3,
                2,
                3,
                List.of(
                        new Schematic.Entry(0, 0, 0, "minecraft:oak_planks"),
                        new Schematic.Entry(1, 0, 0, "minecraft:oak_planks"),
                        new Schematic.Entry(0, 1, 0, "minecraft:oak_planks")));
        Schematic next = new Schematic(
                3,
                2,
                3,
                List.of(
                        new Schematic.Entry(0, 0, 0, "minecraft:stone_bricks"),
                        new Schematic.Entry(1, 0, 0, "minecraft:oak_planks")));

        List<BlockCoord> previousCells = previous.entries(origin).stream()
                .map(Schematic.PlacedEntry::pos)
                .toList();
        List<BlockCoord> extra = SchematicUpgrade.extraPositions(previousCells, next, origin, BuildRotation.NONE);

        assertEquals(
                List.of(new BlockCoord(origin.x(), origin.y() + 1, origin.z())),
                extra,
                "Повышение уровня снимает только блоки, которых нет в новой схеме: лишний верх старого дома");
        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE,
                        extra.get(0),
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.REPAIR,
                        PlanBuilding.HOUSE),
                "Ремонт к новой схеме законно ломает старый лишний блок своей постройки");
        assertInstanceOf(
                PlotProtection.Refused.class,
                PlotProtection.check(
                        SITE,
                        new BlockCoord(origin.x(), origin.y(), origin.z()),
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.NONE,
                        PlanBuilding.NONE),
                "Клетка, совпадающая с новой схемой, остаётся защищённой от обычной ломки");
    }

    @Test
    void selfRescueAndPlayerCommand_LegalGrounds_AreAccepted() {
        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE, MINE, PlotProtection.Action.BREAK, PlotProtection.Ground.SELF_RESCUE, PlanBuilding.NONE),
                "Замурованный дух выбирается из блока шахты: жизнь важнее сохранности клетки");
        assertInstanceOf(
                PlotProtection.Allowed.class,
                PlotProtection.check(
                        SITE,
                        HOUSE,
                        PlotProtection.Action.BREAK,
                        PlotProtection.Ground.PLAYER_COMMAND,
                        PlanBuilding.NONE),
                "Команда игрока снимает защиту явно: это не самовольная ломка духа");
    }

    private static List<BlockCoord> harvestable(List<BlockCoord> cells, PlotProtection.Action action) {
        List<BlockCoord> allowed = new ArrayList<>();
        for (BlockCoord cell : cells) {
            if (PlotProtection.check(SITE, cell, action, PlotProtection.Ground.NONE, PlanBuilding.NONE)
                    instanceof PlotProtection.Allowed) {
                allowed.add(cell);
            }
        }
        return List.copyOf(allowed);
    }

    private static List<BlockCoord> placeable(List<BlockCoord> cells) {
        return harvestable(cells, PlotProtection.Action.PLACE);
    }

    private static List<BlockCoord> removable(List<BlockCoord> cells, PlotProtection.Ground ground, PlanBuilding own) {
        List<BlockCoord> allowed = new ArrayList<>();
        for (BlockCoord cell : cells) {
            if (PlotProtection.check(SITE, cell, PlotProtection.Action.BREAK, ground, own)
                    instanceof PlotProtection.Allowed) {
                allowed.add(cell);
            }
        }
        return List.copyOf(allowed);
    }

    private static boolean protectedByPlan(BlockCoord cell, PlotProtection.Action action) {
        return PlotProtection.check(SITE, cell, action, PlotProtection.Ground.NONE, PlanBuilding.NONE)
                instanceof PlotProtection.Refused;
    }

    private static BlockCoord mineCell(int y) {
        List<ColumnMask.Cell> columns = SITE.mine().mask().cells().stream()
                .filter(cell -> SITE.mineColumn(cell.x(), cell.z()))
                .toList();
        ColumnMask.Cell column = columns.get(0);
        return new BlockCoord(column.x(), y, column.z());
    }

    private static BlockCoord streetCell(int y) {
        ColumnMask.Cell street = SITE.streets().cells().iterator().next();
        return new BlockCoord(street.x(), y, street.z());
    }
}

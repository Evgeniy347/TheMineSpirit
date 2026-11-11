package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlotBreakSeen;
import com.theminespirit.neoforge.board.PlotPlanBlocks;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.movement.RouteLevelView;
import com.theminespirit.neoforge.policy.PlanBreachData;
import com.theminespirit.neoforge.policy.ProtectionGate;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementLevelScenesGameTests {
    private SettlementLevelScenesGameTests() {}

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void settlementPlot_Settle_HeightCountedOnce(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.clear();
        TownPlan plan = plan(helper, 90);
        store.fixTown(plan);
        int x = plan.anchor().x();
        int z = plan.anchor().z();

        helper.assertFalse(store.levelMarked(), "Сохранение старого мира без отметки обязано пересчитать высоту");
        store.settle(new SettlementPlot(new BlockCoord(x, 90, z), 88, 92, 9, "oak"));

        helper.assertTrue(store.levelMarked(), "После обмера высота помечена правилом SettlementLevel");
        helper.assertTrue(
                store.town().orElseThrow().anchor().y() == 90,
                "Высота поселения попадает в план: второго источника (медианы домов) не остаётся");
        BlockCoord stand = new BlockCoord(x + 1, 90, z + 1);
        store.rememberHome(stand);
        helper.assertTrue(
                stand.equals(store.home().orElse(null)),
                "Домашняя стоянка запоминается в мире: цель дома не пересчитывается от текущих позиций духов");
        SettlementPlotStore restored = SettlementPlotStore.load(
                store.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(
                stand.equals(restored.home().orElse(null)),
                "Домашняя стоянка переживает сохранение и рестарт: выданное задание не остаётся со старой целью");
        store.settle(new SettlementPlot(new BlockCoord(x, 94, z), 92, 96, 9, "oak"));
        helper.assertTrue(
                store.town().orElseThrow().anchor().y() == 90,
                "Высота считается один раз: повторный обмер её не переносит");
        helper.assertTrue(
                store.home().isEmpty(),
                "Новая разметка участка сбрасывает запомненную стоянку: дом выбирается заново по новому миру");
        store.clear();
        helper.assertFalse(store.levelMarked(), "Новый мир начинает без отметки высоты");
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public static void rescue_PlanCellBreak_IsAllowedAndRestorable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.clear();
        TownPlan plan = completePlan(helper, 90);
        store.fixTown(plan);
        int x = plan.anchor().x();
        int z = plan.anchor().z();
        store.settle(new SettlementPlot(new BlockCoord(x, 90, z), 88, 92, 9, "oak"));
        StageSite site = SettlementPlotStore.liveSite(helper.getLevel())
                .orElseThrow(() -> new IllegalStateException("Сцена без участка этапа: спасение не на чем проверить"));
        BlockCoord cell = PlotPlanBlocks.of(site).blocks().keySet().stream()
                .filter(at -> site.protectionCell(at.x(), at.y(), at.z()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В плане этапа нет ни одной защищённой клетки постройки"));
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        helper.getLevel().setBlockAndUpdate(pos, Blocks.JUNGLE_PLANKS.defaultBlockState());
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, 1, 5));
        var state = helper.getLevel().getBlockState(pos);

        helper.assertTrue(
                spirit.protectionGate().checkBreakOrEscape(pos, state, true, false) instanceof BlockProtection.Refused,
                "Обычная ломка клетки плана обязана отказывать: спасение — отдельное основание, а не вседозволенность");
        helper.assertTrue(
                spirit.protectionGate().checkBreakOrEscape(pos, state, true, true) instanceof BlockProtection.Allowed,
                "Самоспасение ломает клетку плана: иначе дух в яме дома не выберется (решение владельца)");
        PlanBreachData.Breach breach = PlanBreachData.get(helper.getLevel()).breach(pos);
        helper.assertTrue(
                breach != null && "minecraft:jungle_planks".equals(breach.blockId()),
                "Пролом запоминает тот же блок и духа: заделка возвращает именно его, а не дешёвую замену");
        PlanBreachData.get(helper.getLevel()).clear(pos);
        helper.assertTrue(
                new ProtectionGate(spirit, false).checkEscape(pos, state, true) instanceof BlockProtection.Allowed,
                "Расчёт пути при спасении считает клетку плана проходимой: иначе маршрут обходит дом и дух копает вслепую");
        helper.assertTrue(
                PlanBreachData.get(helper.getLevel()).breach(pos) == null,
                "Поиск пути не записывает пролом: учёт ведёт только настоящее действие, иначе доска «ремонтирует» "
                        + "клетки, которых никто не ломал");
        int logged = PlotBreakSeen.get(helper.getLevel()).size();
        RouteCell.kind(new RouteLevelView(spirit).cell(cell.x(), cell.y(), cell.z()));
        RouteCell.kind(new RouteLevelView(spirit, true).cell(cell.x(), cell.y(), cell.z()));
        BlockCoord spare = PlotPlanBlocks.of(site).blocks().keySet().stream()
                .filter(at -> site.protectionCell(at.x(), at.y(), at.z()) && !at.equals(cell))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("В плане этапа нет второй клетки для проверки журнала"));
        helper.getLevel()
                .setBlockAndUpdate(
                        new BlockPos(spare.x(), spare.y(), spare.z()), Blocks.JUNGLE_PLANKS.defaultBlockState());
        RouteCell.kind(new RouteLevelView(spirit).cell(spare.x(), spare.y(), spare.z()));
        helper.assertTrue(
                RouteCell.kind(new RouteLevelView(spirit).cell(cell.x(), cell.y(), cell.z())) == RouteCell.BLOCKED,
                "Обычный проход не прокапывает клетку плана: обход дома остаётся запретом");
        helper.assertTrue(
                RouteCell.kind(new RouteLevelView(spirit, true).cell(cell.x(), cell.y(), cell.z()))
                        != RouteCell.BLOCKED,
                "Маршрут спасения видит ту же ломку, что разрешает ProtectionGate: иначе клетка плана навсегда "
                        + "закрывает путь наружу");
        helper.assertTrue(
                PlotBreakSeen.get(helper.getLevel()).size() == logged,
                "Поиск пути не пишет журнал отказов: оценка клетки — не ломка, иначе сервер 0.1.3595 забивал "
                        + "журнал тысячами строк на каждый маршрут (board_ms 1514, тик 3,4 с). Оценено клеток: "
                        + (PlotBreakSeen.get(helper.getLevel()).size() - logged));
        spirit.discard();
        store.clear();
        GameTestFixtures.finish(helper);
        helper.succeed();
    }

    private static TownPlan completePlan(GameTestHelper helper, int level) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(10, 0, 10));
        int x1 = Math.min(min.getX(), max.getX());
        int z1 = Math.min(min.getZ(), max.getZ());
        int x2 = Math.max(min.getX(), max.getX());
        int z2 = Math.max(min.getZ(), max.getZ());
        ColumnMask empty = new ColumnMask(x1, z1, x2 - x1 + 1, z2 - z1 + 1, new BitSet());
        List<Placement> places = List.of(
                new Placement("hut", "дом", 1, x1 + 1, z1 + 1, 3, 3, Placement.Facing.NORTH),
                new Placement("yard", "двор", 1, x1 + 5, z1 + 1, 3, 3, Placement.Facing.NORTH),
                new Placement("mine", "шахта", 1, x1 + 1, z1 + 5, 3, 3, Placement.Facing.NORTH),
                new Placement("orchard", "сад", 1, x1 + 5, z1 + 5, 3, 3, Placement.Facing.NORTH));
        return new TownPlan(
                new BlockCoord(x1 + 5, level, z1 + 5),
                ColumnMask.rect(x1, z1, x2, z2),
                new TownStreets(ColumnMask.rect(x1, z1, x2, z1), empty, empty),
                places,
                new FenceLine(List.of(), List.of()),
                List.of());
    }

    private static TownPlan plan(GameTestHelper helper, int level) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(10, 0, 10));
        int x1 = Math.min(min.getX(), max.getX());
        int z1 = Math.min(min.getZ(), max.getZ());
        int x2 = Math.max(min.getX(), max.getX());
        int z2 = Math.max(min.getZ(), max.getZ());
        ColumnMask empty = new ColumnMask(x1, z1, x2 - x1 + 1, z2 - z1 + 1, new BitSet());
        return new TownPlan(
                new BlockCoord(x1 + 5, level, z1 + 5),
                ColumnMask.rect(x1, z1, x2, z2),
                new TownStreets(ColumnMask.rect(x1, z1, x2, z1), empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}

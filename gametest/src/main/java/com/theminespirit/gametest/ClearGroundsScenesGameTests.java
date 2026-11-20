package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClearGroundsScenesGameTests {
    private static final BlockPos MOUND = new BlockPos(4, 1, 4);
    private static final BlockPos SPIRIT = new BlockPos(6, 1, 4);

    private ClearGroundsScenesGameTests() {}

    @GameTest(template = "mine_arena", batch = "scene_clear_grounds", timeoutTicks = 400)
    public static void clearArea_PlotGroundInZone_RemovedByClearArea(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SettlementPlotStore store = SettlementPlotStore.get(helper.getLevel());
        store.clear();
        TownPlan plan = plan(helper, 90);
        store.fixTown(plan);
        store.settle(new SettlementPlot(
                new BlockCoord(plan.anchor().x(), 90, plan.anchor().z()), 88, 92, 9, "oak"));
        helper.setBlock(MOUND, Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT);
        spirit.getInventory().addItem(new ItemStack(Items.IRON_SHOVEL));
        ScriptCall clear = clearCall(helper, MOUND);
        SpiritRun run = SpiritRun.launch(helper, spirit, clear, 1);
        helper.succeedWhen(() -> assertCleared(helper, spirit, run, store));
    }

    private static void assertCleared(
            GameTestHelper helper, SpiritEntity spirit, SpiritRun run, SettlementPlotStore store) {
        CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
        helper.assertTrue(
                helper.getBlockState(MOUND).isAir(),
                "грунт в зоне поселения вне клеток плана обязан убираться расчисткой: в "
                        + MOUND + " остался " + helper.getBlockState(MOUND)
                        + ", причина задачи: " + spirit.taskFailureReason().orElse("нет"));
        GameTestFixtures.finish(helper, 16);
        spirit.discard();
        store.clear();
    }

    private static ScriptCall clearCall(GameTestHelper helper, BlockPos cell) {
        BlockPos at = helper.absolutePos(cell);
        return new ScriptCall(
                "clear_area",
                Map.of(
                        "x1", at.getX(),
                        "y1", at.getY(),
                        "z1", at.getZ(),
                        "x2", at.getX(),
                        "y2", at.getY(),
                        "z2", at.getZ()));
    }

    private static TownPlan plan(GameTestHelper helper, int level) {
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
}

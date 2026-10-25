package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FillPitGameTests {
    private static final int GROUND = 4;
    private static final int PIT_FROM = 4;
    private static final int PIT_TO = 6;
    private static final int SETTLE_TICKS = 5;
    private static final int NARROW = 5;
    private static final int CANOPY = 9;
    private static final int NARROW_TOP = 4;

    private FillPitGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void fill_SpiritAtPitBottom_FillsUnderItselfAndClimbs(GameTestHelper helper) {
        run(helper, new BlockPos(5, 1, 5));
    }

    @SceneStep(template = "mine_arena", timeoutTicks = 400)
    public static void fill_NarrowPitUnderLeaves_FilledToTop(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                for (int y = 1; y <= NARROW_TOP; y++) {
                    if (!(x == NARROW && z == NARROW && y > 1)) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        for (int x = NARROW - 1; x <= NARROW + 1; x++) {
            for (int z = NARROW - 1; z <= NARROW + 1; z++) {
                helper.setBlock(
                        new BlockPos(x, CANOPY, z),
                        Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            }
        }
        BlockPos anchor = helper.absolutePos(new BlockPos(NARROW, NARROW_TOP + 1, NARROW));
        SettlementPlotStore.get(helper.getLevel())
                .fix(new SettlementPlot(
                        new BlockCoord(anchor.getX(), anchor.getY(), anchor.getZ()),
                        anchor.getY(),
                        anchor.getY(),
                        1,
                        "oak"));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(NARROW - 2, NARROW_TOP + 1, NARROW));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        BlockPos from = helper.absolutePos(new BlockPos(NARROW, 2, NARROW));
        BlockPos to = helper.absolutePos(new BlockPos(NARROW, NARROW_TOP, NARROW));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "fill",
                        Map.of(
                                "block",
                                "dirt",
                                "x1",
                                from.getX(),
                                "y1",
                                from.getY(),
                                "z1",
                                from.getZ(),
                                "x2",
                                to.getX(),
                                "y2",
                                to.getY(),
                                "z2",
                                to.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            int filled = 0;
            for (int y = 2; y <= NARROW_TOP; y++) {
                filled += helper.getBlockState(new BlockPos(NARROW, y, NARROW)).is(Blocks.DIRT) ? 1 : 0;
            }
            helper.assertTrue(
                    filled == NARROW_TOP - 1,
                    "яма 1×1×3 под кроной засыпана до верха: поставлено " + filled + " из " + (NARROW_TOP - 1)
                            + ", статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            SettlementPlotStore.get(helper.getLevel()).clear();
            GameTestFixtures.finish(helper);
        });
    }

    private static void run(GameTestHelper helper, BlockPos start) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                boolean pit = x >= PIT_FROM && x <= PIT_TO && z >= PIT_FROM && z <= PIT_TO;
                for (int y = 1; y <= GROUND && !pit; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), start);
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 64));
        List<TaskNode> rows = new ArrayList<>();
        for (int y = 1; y <= GROUND; y++) {
            BlockPos from = helper.absolutePos(new BlockPos(PIT_FROM, y, PIT_FROM));
            BlockPos to = helper.absolutePos(new BlockPos(PIT_TO, y, PIT_TO));
            rows.add(new ScriptCall(
                    "fill",
                    Map.of(
                            "block",
                            "dirt",
                            "x1",
                            from.getX(),
                            "y1",
                            from.getY(),
                            "z1",
                            from.getZ(),
                            "x2",
                            to.getX(),
                            "y2",
                            to.getY(),
                            "z2",
                            to.getZ())));
        }
        SpiritRun run = SpiritRun.launch(helper, spirit, new SequenceNode(rows), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "подсыпка ямы обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int filled = 0;
            for (int x = PIT_FROM; x <= PIT_TO; x++) {
                for (int z = PIT_FROM; z <= PIT_TO; z++) {
                    for (int y = 1; y <= GROUND; y++) {
                        filled += helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT) ? 1 : 0;
                    }
                }
            }
            helper.assertTrue(filled == 36, "яма 3×3×4 засыпана целиком: поставлено " + filled + " из 36");
            helper.assertTrue(
                    helper.relativePos(spirit.blockPosition()).getY() > GROUND,
                    "дух наверху, а не в яме: стоит на y "
                            + helper.relativePos(spirit.blockPosition()).getY());
            GameTestFixtures.finish(helper);
        });
    }
}

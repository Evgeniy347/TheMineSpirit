package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseData;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
public final class BoardScenesGameTests {
    private BoardScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_board_arena", timeoutTicks = 2403)
    public static void boardScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), BoardGameTests.class, BoardV2GameTests.class);
    }

    @GameTest(template = "big_arena", batch = "scene_undo_stall", timeoutTicks = 200)
    public static void undoScene_InReachButInvisibleCell_FailsWithReasonKeepsLeaseOrphaned(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 24);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 2));
        spirit.getInventory().addItem(new ItemStack(Items.STONE_PICKAXE));
        BlockPos cell = new BlockPos(3, 0, 2);
        helper.setBlock(cell, Blocks.DIRT);
        helper.setBlock(cell.above(), Blocks.STONE);
        LeaseRun abandoned = LeaseRun.open(spirit, "scaffold");
        abandoned.placed(helper.absolutePos(cell), "minecraft:dirt");
        abandoned.orphan();
        withUndoNoProgress(20);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("undo_lease", Map.of("id", abandoned.id())), 1);
        restoreUndoNoProgressWhenFinished(helper, run);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.FAILED);
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("без продвижения"),
                    "Откат без продвижения обязан провалиться с причиной, а не висеть RUNNING: "
                            + spirit.taskFailureReason().orElse("нет"));
            Lease lease = LeaseData.get(helper.getLevel()).book().require(abandoned.id());
            helper.assertTrue(
                    lease.status() == LeaseStatus.ORPHANED,
                    "Провал отката оставляет аренду ORPHANED до смены ключа, а не закрывает и не теряет её: "
                            + lease.status());
            helper.assertTrue(
                    helper.getBlockState(cell).is(Blocks.DIRT),
                    "Провал не трогает мир: клетка " + cell + " обязана остаться " + Blocks.DIRT + ", сейчас "
                            + helper.getBlockState(cell));
            spirit.discard();
            GameTestFixtures.finish(helper, 24);
        });
    }

    @GameTest(template = "arena", batch = "scene_dig_open_pit", timeoutTicks = 400)
    public static void digOut_OpenPitUnderPlateau_StepsOutBeforeBackfill(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 2; x <= 6; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        BlockPos feet = new BlockPos(4, 1, 4);
        helper.setBlock(feet, Blocks.AIR);
        helper.setBlock(feet.above(), Blocks.AIR);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), feet);
        spirit.getInventory().addItem(new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().addItem(new ItemStack(Items.DIRT, 8));
        BlockPos home = helper.absolutePos(new BlockPos(6, 3, 4));
        BlockCoord goal = new BlockCoord(home.getX(), home.getY(), home.getZ());
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("dig_out", Map.of("x", goal.x(), "y", goal.y(), "z", goal.z(), "rescue", true)),
                1);

        helper.succeedWhen(() -> {
            CraftTestSupport.assertFinished(helper, spirit, run, RunStatus.SUCCEEDED);
            helper.assertTrue(
                    spirit.blockPosition().getY() >= home.getY(),
                    "дух обязан выйти из ямы на площадку до возврата spoil: стоит в " + spirit.blockPosition()
                            + ", цель " + home + ", иначе dig_out закрывается, заделывает ступень и оставляет "
                            + "духа в той же яме (живой случай spirit-1 -195,65,-497)");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(5, 2, 4)).is(Blocks.STONE),
                    "ступень, по которой дух вышел, возвращается на место после выхода: в (5,2,4) сейчас "
                            + helper.getBlockState(new BlockPos(5, 2, 4)));
            spirit.discard();
            GameTestFixtures.finish(helper);
        });
    }

    private static void withUndoNoProgress(int ticks) {
        BoardConfig base = BoardConfig.readFromClasspath();
        Map<String, Long> intervals = new LinkedHashMap<>(base.intervals());
        intervals.put("undoNoProgressTicks", (long) ticks);
        BoardConfig.useSource(() -> new BoardConfig(
                base.priorities(),
                base.modifiers(),
                intervals,
                base.levels(),
                base.herd(),
                base.immovable(),
                base.products(),
                base.tools(),
                base.wool(),
                base.hive()));
    }

    private static void restoreUndoNoProgressWhenFinished(GameTestHelper helper, SpiritRun run) {
        AtomicBoolean restored = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (run.started()
                    && run.spirit().taskStatus().orElse(null) != RunStatus.RUNNING
                    && restored.compareAndSet(false, true)) {
                BoardConfig.useSource(BoardConfig::readFromClasspath);
            }
        });
    }
}

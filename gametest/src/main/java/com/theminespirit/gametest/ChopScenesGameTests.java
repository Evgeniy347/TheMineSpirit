package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopScenesGameTests {
    private ChopScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_chop_arena", timeoutTicks = 801)
    public static void chopScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), ChopReplantGameTests.class);
    }

    @GameTest(template = "mine_arena", batch = "scene_chop_mine_arena", timeoutTicks = 1801)
    public static void chopScene_MineArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("mine_arena"), ChopBigTreesGameTests.class);
    }

    @GameTest(template = "arena", batch = "scene_chop_unreachable", timeoutTicks = 400)
    public static void chopTree_SealedStand_FailsInsteadOfReportingSuccess(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        ChopSceneSupport.prepareField(helper);
        int cx = 5;
        int cz = 5;
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                for (int y = 1; y <= 5; y++) {
                    helper.setBlock(x, y, z, Blocks.BARRIER);
                }
            }
        }
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(cx, y, cz, Blocks.OAK_LOG);
        }
        var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(cx, 3, cz - 1, leaf);
        helper.setBlock(cx, 3, cz + 1, leaf);
        helper.setBlock(cx, 4, cz, leaf);
        for (int side : new int[] {cx - 1, cx + 1}) {
            helper.setBlock(side, 1, cz, Blocks.AIR);
            helper.setBlock(side, 2, cz, Blocks.AIR);
        }
        SpiritEntity spirit = spawnChopper(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дерево за непроходимой оболочкой: рубка обязана честно провалиться, а не закрыться успехом;"
                            + " статус " + spirit.taskStatus().map(Enum::name).orElse("нет")
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(cx, 1, cz)).is(Blocks.OAK_LOG),
                    "брёвна недостижимого дерева остаются в мире — задачу нельзя считать выполненной");
        });
    }
}

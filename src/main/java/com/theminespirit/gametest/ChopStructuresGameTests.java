package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildLogPillar;
import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildPlankHutOnLedge;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopTestSupport.BIG_BUILDING_LOGS;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.FIELD_HEIGHT;
import static com.theminespirit.gametest.ChopTestSupport.HUT_POST_LOGS;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.chopParams;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.driveScript;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.neoforge.ChopTreeScript;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopStructuresGameTests {
    private ChopStructuresGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_OakOnSlopeWithBranchOverGrass_ChoppedWithBranch(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        helper.setBlock(5, 1, 4, Blocks.GRASS_BLOCK);
        helper.setBlock(
                5, 2, 4, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "настоящий дуб на склоне, ветка которого висит над травой выше основания, обязан рубиться: "
                            + "почва под веткой не признак постройки. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    logsInInventory == 6,
                    "ствол из 5 брёвен и ветка над склоном — 6 брёвен в инвентаре, фактически " + logsInInventory);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.OAK_LOG) == 0,
                    "в мире не должно остаться брёвен дерева на склоне");

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 260)
    public static void chopTree_LogPostsWithPlankWallsUnderCrown_UntouchedAndRealOakChopped(GameTestHelper helper) {
        prepareField(helper);
        buildPlankHutOnLedge(helper, 3, 3);
        buildOakTree(helper, 8, 8, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "хижина с досками обязана остаться, а настоящий дуб за ней срубиться. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int postLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    postLogs == HUT_POST_LOGS,
                    "все " + HUT_POST_LOGS + " брёвна угловых столбов хижины обязаны остаться: столб с листьями "
                            + "неотличим от ствола, кроме досок стены вплотную. Осталось " + postLogs);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(4, 2, 3)).is(BlockTags.DOORS)
                            && helper.getBlockState(new BlockPos(4, 3, 5)).is(Blocks.GLASS)
                            && helper.getBlockState(new BlockPos(4, 2, 4)).is(Blocks.TORCH),
                    "дверь, стекло и факел хижины обязаны остаться на месте");
            int oakInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    oakInInventory == 5,
                    "настоящий дуб срублен целиком: 5 брёвен в инвентаре, фактически " + oakInInventory);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 200)
    public static void chopTree_LogPostByCobblestoneWallUnderForeignCrown_NotChoppedRealOakChopped(
            GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 6, 4, 5);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(5, 5, 3, leaves);
        helper.setBlock(5, 5, 5, leaves);
        buildLogPillar(helper, 4, 4, 4);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(3, y, 4, Blocks.COBBLESTONE);
        }
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дуб рядом со стеной из булыжника обязан срубиться, а столб под чужой кроной — остаться: "
                            + "булыжник не признак постройки, и столб с листьями неотличим от ствола. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int oakInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    oakInInventory == 5,
                    "настоящий дуб срублен целиком: 5 брёвен в инвентаре, фактически " + oakInInventory);
            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    logsInWorld == 4,
                    "4 бревна столба у стены из булыжника обязаны остаться нетронутыми, осталось " + logsInWorld);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.COBBLESTONE) == 4,
                    "стена из булыжника обязана остаться целиком: 4 блока, фактически "
                            + countBlocksInWorld(helper, Blocks.COBBLESTONE));

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 200)
    public static void chopTree_LogBuildingOfManyLogs_TreeBehindItIsChopped(GameTestHelper helper) {
        prepareField(helper);
        for (int x = 3; x <= 9; x++) {
            for (int y = 1; y <= 7; y++) {
                for (int z = 3; z <= 9; z++) {
                    helper.setBlock(x, y, z, Blocks.OAK_LOG);
                }
            }
        }
        buildOakTree(helper, 1, 8, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "постройка из " + BIG_BUILDING_LOGS + " брёвен всех отвергнута, а дерево за ней обязано срубиться: "
                            + "лимит кандидатов не считает отвергнутых, иначе поиск не идёт дальше и скрипт висит. "
                            + "Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            int oakInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    oakInInventory == 5,
                    "дерево за постройкой срублено: 5 брёвен в инвентаре, фактически " + oakInInventory);
            int buildingLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    buildingLogs == BIG_BUILDING_LOGS,
                    "ни одно из " + BIG_BUILDING_LOGS + " брёвен постройки не тронуто, осталось " + buildingLogs);

            clearField(helper);
        });
    }

    // [флак] далёкая ветка за стеной: дух рубит её с одной стоянки через стену; потолок с запасом
    @SceneStep(template = FIELD, timeoutTicks = 500)
    public static void chopTree_FarBranchBehindWall_ChoppedFromOneStand(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        for (int x = 5; x <= 10; x++) {
            helper.setBlock(x, 6, 4, Blocks.OAK_LOG);
        }
        helper.setBlock(5, 5, 4, Blocks.AIR);
        for (int z = -2; z <= 12; z++) {
            for (int y = 1; y <= 5; y++) {
                helper.setBlock(8, y, z, Blocks.STONE);
            }
        }
        SpiritEntity spirit = spawnChopper(helper);
        ChopTreeScript script = new ChopTreeScript(spirit, chopParams("oak", 1));
        ScriptStatus[] status = driveScript(helper, spirit, script);

        BlockPos beyond = helper.absolutePos(new BlockPos(8, 0, -2));
        AABB beyondWall = new AABB(
                beyond.getX(),
                beyond.getY(),
                beyond.getZ(),
                beyond.getX() + 4,
                beyond.getY() + FIELD_HEIGHT,
                beyond.getZ() + 16);
        helper.onEachTick(() -> helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, beyondWall)
                .forEach(ItemEntity::discard));

        helper.succeedWhen(() -> {
            String state =
                    "статус " + status[0] + ", причина: " + script.failureReason() + ", итог: " + script.summary();
            helper.assertTrue(
                    status[0] == ScriptStatus.SUCCEEDED,
                    "далёкая ветка за стеной обязана быть срублена с одной стоянки: " + state);
            int left = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    left == 0,
                    "все брёвна дерева, включая ветку за стеной, обязаны быть срублены: в мире " + left + ", а "
                            + state);

            clearField(helper);
        });
    }
}

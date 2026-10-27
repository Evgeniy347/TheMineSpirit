package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildSquaredTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopTestSupport.LOGGER;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.TALL_FIELD_HEIGHT;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class ChopBigTreesGameTests {
    private ChopBigTreesGameTests() {}

    @SceneStep(template = "mine_arena", timeoutTicks = 400)
    public static void chopTree_NineLogTrunk_AllLogsChoppedFromOneStand(GameTestHelper helper) {
        clearField(helper, TALL_FIELD_HEIGHT);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        buildTree(helper, 4, 4, 9, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дерево высотой 9: дух рубит все брёвна с одной стоянки без столба. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            int logsOnGround = GameTestFixtures.countInWorld(helper, Items.OAK_LOG);
            helper.assertTrue(
                    logsInInventory + logsOnGround == 9,
                    "все 9 брёвен обязаны сохраниться в инвентаре или на земле: в инвентаре " + logsInInventory
                            + ", на земле " + logsOnGround
                            + "; дух на " + spirit.blockPosition().toShortString()
                            + ", тиков духа " + run.spiritTicks()
                            + ", брёвен-блоков в мире " + countBlocksInWorld(helper, Blocks.OAK_LOG)
                            + ", булыжника-блоков в мире " + countBlocksInWorld(helper, Blocks.COBBLESTONE));
            helper.assertTrue(
                    logsOnGround == 0, "дроп со всего дерева обязан быть подобран: на земле осталось " + logsOnGround);
            int logsInWorld = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    logsInWorld == 0, "в мире не должно остаться ни одного бревна дерева, осталось " + logsInWorld);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.COBBLESTONE) == 0,
                    "столб не строится: булыжника в мире быть не должно");
            LOGGER.info("chop_tree, дерево 9 с одной стоянки: {} тиков духа", run.spiritTicks());

            clearField(helper, TALL_FIELD_HEIGHT);
        });
    }

    // [флак] замер 885 тиков: 80 брёвен 2×2 высотой 20 рубятся с одной стоянки; потолок 1800 ≈ 2× замера
    @SceneStep(template = "mine_arena", timeoutTicks = 1800)
    public static void chopTree_GiantSpruceTwoByTwoHeightTwenty_ChoppedFromOneStand(GameTestHelper helper) {
        clearField(helper, 22);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        buildSquaredTree(helper, 4, 4, 20, 20, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
        SpiritEntity spirit = spawnChopper(helper);

        BlockPos[] standAt = {null};
        double[] maxShift = {0};
        helper.onEachTick(() -> {
            if (standAt[0] == null) {
                if (helper.getBlockState(new BlockPos(4, 20, 4)).isAir()) {
                    standAt[0] = spirit.blockPosition();
                }
                return;
            }
            if (!helper.getBlockState(new BlockPos(4, 1, 4)).isAir()) {
                maxShift[0] = Math.max(maxShift[0], standAt[0].distSqr(spirit.blockPosition()));
            }
        });

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("spruce", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ель 2×2 высотой 20: всё дерево рубится с одной стоянки без столба. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInWorld = countBlocksInWorld(helper, Blocks.SPRUCE_LOG);
            helper.assertTrue(logsInWorld == 0, "все 80 брёвен ели 2×2 обязаны быть срублены, осталось " + logsInWorld);
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.SPRUCE_LOG);
            int logsOnGround = GameTestFixtures.countInWorld(helper, Items.SPRUCE_LOG);
            helper.assertTrue(
                    logsInInventory + logsOnGround == 80,
                    "все 80 брёвен ели обязаны сохраниться: в инвентаре " + logsInInventory + ", на земле "
                            + logsOnGround);
            helper.assertTrue(
                    logsOnGround == 0, "дроп со всего дерева обязан быть подобран: на земле осталось " + logsOnGround);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.COBBLESTONE) == 0,
                    "столб не строится: булыжника в мире быть не должно");
            helper.assertTrue(
                    standAt[0] != null, "рубка обязана начаться: верхнее бревно срублено, стоянка зафиксирована");
            helper.assertTrue(
                    maxShift[0] <= 1,
                    "после подхода дух рубит с одной стоянки: сдвиг за всю рубку не больше 1 блока, " + "а он "
                            + maxShift[0] + " (квадрат расстояния от стоянки " + standAt[0] + ")");
            LOGGER.info("chop_tree, ель 2×2 высотой 20 с одной стоянки: {} тиков духа", run.spiritTicks());

            clearField(helper, 22);
        });
    }
}

package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildSquaredTree;
import static com.theminespirit.gametest.ChopSceneSupport.buildTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopTestSupport.LOGGER;
import static com.theminespirit.gametest.ChopTestSupport.PLATFORM;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.List;
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
final class ChopBigTreesGameTests {
    private ChopBigTreesGameTests() {}

    // [флак] замер 885 тиков: 80 брёвен 2×2 высотой 20 рубятся с одной стоянки; потолок 1800 ≈ 2× замера
    @SceneStep(template = "mine_arena", timeoutTicks = 1800)
    public static void chopTree_GiantSpruceTwoByTwoHeightTwenty_ChoppedFromOneStand(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        clearField(helper, 22);
        for (int x = 0; x < PLATFORM; x++) {
            for (int z = 0; z < PLATFORM; z++) {
                helper.setBlock(x, 0, z, Blocks.DIRT);
            }
        }
        BlockPos hanging = new BlockPos(3, 1, 1);
        helper.setBlock(hanging.getX(), 4, hanging.getZ(), Blocks.DIRT);
        for (int y = 5; y <= 20; y++) {
            helper.setBlock(hanging.getX(), y, hanging.getZ(), Blocks.JUNGLE_LOG);
        }
        ChopSceneSupport.addLeavesCrown(helper, hanging.getX(), hanging.getZ(), 20, Blocks.JUNGLE_LEAVES);
        helper.setBlock(hanging.getX() + 1, 20, hanging.getZ(), Blocks.JUNGLE_LOG);
        helper.setBlock(hanging.getX() + 2, 20, hanging.getZ(), Blocks.JUNGLE_LOG);
        helper.setBlock(hanging.getX() + 3, 20, hanging.getZ(), Blocks.JUNGLE_LOG);
        var branchLeaves = Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        helper.setBlock(hanging.getX() + 2, 21, hanging.getZ(), branchLeaves);
        helper.setBlock(hanging.getX() + 3, 21, hanging.getZ(), branchLeaves);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = -4; y <= 3; y++) {
                    helper.setBlock(hanging.getX() + dx, y, hanging.getZ() + dz, Blocks.AIR);
                }
                helper.setBlock(hanging.getX() + dx, -4, hanging.getZ() + dz, Blocks.WATER);
            }
        }
        buildSquaredTree(helper, 4, 4, 20, 20, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES);
        buildOakTree(helper, 8, 8, 5);
        helper.setBlock(9, 3, 8, Blocks.OAK_LOG);
        helper.setBlock(10, 3, 8, Blocks.OAK_LOG);
        helper.setBlock(10, 4, 8, Blocks.OAK_LOG);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false);
        for (int dx = 8; dx <= 10; dx++) {
            for (int dz = 7; dz <= 9; dz++) {
                for (int y = 3; y <= 5; y++) {
                    BlockPos pos = new BlockPos(dx, y, dz);
                    if (helper.getBlockState(pos).isAir()) {
                        helper.setBlock(pos, leaves);
                    }
                }
            }
        }
        BrokenAxes broken = brokenAxes(helper);
        SpiritEntity spirit = spawnChopper(helper);
        SpiritEntity climber = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(8, 1, 1));
        climber.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        climber.getInventory().addItem(new ItemStack(Items.DIRT, 32));
        SpiritRun climbRun = SpiritRun.launch(helper, climber, chopCall("jungle", 1), SETTLE_TICKS);
        int groundY = helper.absolutePos(BlockPos.ZERO).getY();
        int[] climbTop = {Integer.MIN_VALUE};
        helper.onEachTick(() ->
                climbTop[0] = Math.max(climbTop[0], climber.blockPosition().getY() - groundY));

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

        SpiritRun run = SpiritRun.launch(
                helper, spirit, new SequenceNode(List.of(chopCall("spruce", 1), chopCall("oak", 1))), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ель 2×2 высотой 20: всё дерево рубится с одной стоянки без столба. Статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInWorld = countBlocksInWorld(helper, Blocks.SPRUCE_LOG);
            helper.assertTrue(logsInWorld == 0, "все 80 брёвен ели 2×2 обязаны быть срублены, осталось " + logsInWorld);
            int logsInInventory = spruce(spirit) + broken.spruce();
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
            int oakLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    oakLogs == 0,
                    "дуб с боковой веткой обязан срубиться целиком: пня и висящей верхушки не остаётся, " + "а в мире "
                            + oakLogs);
            broken.assertDone(helper);
            climbRun.assertStarted();
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.JUNGLE_LOG) == 0,
                    "все брёвна ствола над ямой срублены без ходимых стоянок: осталось "
                            + countBlocksInWorld(helper, Blocks.JUNGLE_LOG));
            helper.assertTrue(
                    climbTop[0] >= 20,
                    "без подъёма верх ветки над ямой не срубить: дух поднимался только до " + climbTop[0]
                            + " (нужна стоянка на 20)");
            List<Lease> scaffolds = LeaseData.get(helper.getLevel()).book().all().stream()
                    .filter(lease -> "scaffold".equals(lease.purpose()) && lease.ownedBy(climber.getUUID()))
                    .toList();
            helper.assertTrue(
                    !scaffolds.isEmpty() && scaffolds.stream().allMatch(lease -> lease.status() == LeaseStatus.DONE),
                    "подъём к стволу идёт на аренде подмостей и закрывается DONE после спуска: " + scaffolds);
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

    private record BrokenAxes(SpiritEntity mid, SpiritEntity last, SpiritRun midRun, SpiritRun lastRun) {
        int spruce() {
            return ChopBigTreesGameTests.spruce(mid) + ChopBigTreesGameTests.spruce(last);
        }

        void assertDone(GameTestHelper helper) {
            helper.assertTrue(
                    midRun.started() && lastRun.started(), "оба духа с ломающимися топорами обязаны начать рубку");
            helper.assertTrue(
                    mid.taskStatus().orElse(null) == RunStatus.SUCCEEDED
                            && last.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "поломка топора на середине и на последнем блоке не бросает дерево: статусы "
                            + mid.taskStatus().orElse(null) + "/"
                            + last.taskStatus().orElse(null) + ", причины: "
                            + mid.taskFailureReason().orElse("нет") + " / "
                            + last.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.BIRCH_LOG) == 0,
                    "берёзы дорубаются после поломки топора: брёвен не осталось, пни убраны");
            helper.assertTrue(
                    mid.getInventory().getItem(0).isEmpty()
                            && last.getInventory().getItem(0).isEmpty(),
                    "топоры с ограниченным запасом обязаны сломаться в сцене, иначе поломка не проверена");
        }
    }

    private static BrokenAxes brokenAxes(GameTestHelper helper) {
        buildTree(helper, 1, 5, 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        buildTree(helper, 8, 5, 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES);
        SpiritEntity mid = spawnChopperAt(helper, new BlockPos(0, 1, 5), 1);
        SpiritEntity last = spawnChopperAt(helper, new BlockPos(9, 1, 5), 3);
        SpiritRun midRun = SpiritRun.launch(helper, mid, chopCall("birch", 1), SETTLE_TICKS);
        SpiritRun lastRun = SpiritRun.launch(helper, last, chopCall("birch", 1), SETTLE_TICKS);
        return new BrokenAxes(mid, last, midRun, lastRun);
    }

    private static int spruce(SpiritEntity spirit) {
        ItemStack hand = spirit.getMainHandItem();
        return GameTestFixtures.countInInventory(spirit.getInventory(), Items.SPRUCE_LOG)
                + (hand.is(Items.SPRUCE_LOG) ? hand.getCount() : 0);
    }

    private static SpiritEntity spawnChopperAt(GameTestHelper helper, BlockPos pos, int uses) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), pos);
        ItemStack axe = new ItemStack(Items.WOODEN_AXE);
        axe.setDamageValue(axe.getMaxDamage() - uses);
        spirit.getInventory().setItem(0, axe);
        return spirit;
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritGestureGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final double SIGHT = 8.0;

    private SpiritGestureGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void placeBlock_TargetCell_SpiritLooksAtItAndSwingsArm(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos target = new BlockPos(4, 1, 6);
        BlockPos absolute = helper.absolutePos(target);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(2, 1, 3));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 1));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "place_block",
                        Map.of(
                                "block",
                                "cobblestone",
                                "x",
                                absolute.getX(),
                                "y",
                                absolute.getY(),
                                "z",
                                absolute.getZ())),
                SETTLE_TICKS);
        Watch watch = new Watch();
        helper.onEachTick(() -> watch.observe(helper, spirit, absolute, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Блок обязан встать; статус " + spirit.taskStatus());
            watch.assertGesture(helper, "постановке");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 300)
    public static void clearArea_SingleBlock_SpiritLooksAtItAndSwingsArm(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        BlockPos target = new BlockPos(6, 1, 4);
        helper.setBlock(target, Blocks.COBBLESTONE);
        BlockPos absolute = helper.absolutePos(target);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 2));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE, 1));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "clear_area",
                        Map.of(
                                "x1",
                                absolute.getX(),
                                "y1",
                                absolute.getY(),
                                "z1",
                                absolute.getZ(),
                                "x2",
                                absolute.getX(),
                                "y2",
                                absolute.getY(),
                                "z2",
                                absolute.getZ())),
                SETTLE_TICKS);
        Watch watch = new Watch();
        helper.onEachTick(() -> watch.observe(helper, spirit, absolute, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Блок обязан сломаться; статус " + spirit.taskStatus());
            watch.assertGesture(helper, "ломании");
            GameTestFixtures.finish(helper);
        });
    }

    private static final class Watch {
        private boolean changed;
        private boolean lookedAtChange;
        private boolean swungAtChange;
        private boolean armMoved;

        void observe(GameTestHelper helper, SpiritEntity spirit, BlockPos target, boolean placing) {
            armMoved |= spirit.swingTime > 0;
            boolean present = helper.getLevel().getBlockState(target).is(Blocks.COBBLESTONE);
            if (changed || present != placing) {
                return;
            }
            changed = true;
            Vec3 eye = spirit.getEyePosition();
            Vec3 end = eye.add(spirit.getViewVector(1.0F).scale(SIGHT));
            lookedAtChange = new AABB(target).clip(eye, end).isPresent();
            swungAtChange = spirit.swinging;
        }

        void assertGesture(GameTestHelper helper, String action) {
            helper.assertTrue(changed, "Изменение блока не замечено: проверять жест не на чем");
            helper.assertTrue(
                    lookedAtChange,
                    "При " + action + " вектор взгляда духа обязан указывать на клетку блока: игрок видит, над чем "
                            + "дух работает (SpiritGesture.face)");
            helper.assertTrue(swungAtChange, "При " + action + " дух обязан взмахнуть рукой (swing)");
            helper.assertTrue(
                    armMoved,
                    "Взмах обязан анимироваться (swingTime > 0): без updateSwingTime в aiStep рука духа не опускается");
        }
    }
}

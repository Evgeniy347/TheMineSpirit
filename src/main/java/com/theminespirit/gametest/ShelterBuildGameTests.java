package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShelterBuildGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final BlockPos START = new BlockPos(4, 1, 4);
    private static final int BODY_BLOCKS = 130;

    private ShelterBuildGameTests() {}

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.DIRT);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, ItemStack... supplies) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), START);
        for (int slot = 0; slot < supplies.length; slot++) {
            spirit.getInventory().setItem(slot, supplies[slot]);
        }
        return spirit;
    }

    private static int count(SpiritEntity spirit, Item item) {
        return GameTestFixtures.countInInventory(spirit.getInventory(), item);
    }

    private static boolean outsideBody(GameTestHelper helper, SpiritEntity spirit) {
        BlockPos origin = helper.absolutePos(START).offset(-3, -1, -3);
        BlockPos feet = spirit.blockPosition();
        return feet.getX() < origin.getX()
                || feet.getX() >= origin.getX() + 6
                || feet.getZ() < origin.getZ() + 1
                || feet.getZ() >= origin.getZ() + 7;
    }

    // [флак] полная схема 6×6: расчистка 36 клеток земли, план, 130 блоков корпуса, полублок и факел;
    // запас на подходы полного прогона
    @SceneStep(template = "arena", timeoutTicks = 6000)
    public static void shelter_CheapMaterial_BuildsAndSpiritLeaves(GameTestHelper helper) {
        prepare(helper);
        BlockPos origin = START.offset(-3, -1, -3);
        SpiritEntity spirit = spawnWith(
                helper,
                new ItemStack(Items.JUNGLE_LOG, 64),
                new ItemStack(Items.SMOOTH_STONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 4),
                new ItemStack(Items.COBBLESTONE_SLAB, 1),
                new ItemStack(Items.TORCH, 1),
                new ItemStack(Items.STONE_SHOVEL, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_shelter", Map.of()), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "укрытие 6×6 из булыжника обязано построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            BlockPos front = origin.offset(0, 1, 1);
            BlockPos roof = origin.offset(3, 4, 4);
            BlockPos entry = origin.offset(3, 1, 1);
            helper.assertTrue(
                    helper.getBlockState(front).is(Blocks.COBBLESTONE),
                    "стена укрытия обязана быть из булыжника, а там "
                            + helper.getBlockState(front).getBlock());
            helper.assertTrue(
                    helper.getBlockState(roof).is(Blocks.COBBLESTONE),
                    "потолок укрытия обязан быть из булыжника, а там "
                            + helper.getBlockState(roof).getBlock());
            helper.assertTrue(
                    helper.getBlockState(START.below()).is(Blocks.COBBLESTONE),
                    "пол укрытия обязан быть из булыжника, а там "
                            + helper.getBlockState(START.below()).getBlock());
            helper.assertTrue(
                    helper.getBlockState(entry).isAir()
                            && helper.getBlockState(entry.above()).isAir(),
                    "проём входа 1×2 обязан остаться свободным: иначе строитель замурован (INC-10)");
            helper.assertTrue(
                    helper.getBlockState(START).isAir()
                            && helper.getBlockState(START.above()).isAir(),
                    "клетки духа внутри укрытия обязаны остаться свободными");
            helper.assertTrue(
                    helper.getBlockState(origin.offset(3, 1, 4)).is(Blocks.TORCH),
                    "внутри укрытия обязан стоять факел, а там "
                            + helper.getBlockState(origin.offset(3, 1, 4)).getBlock());
            helper.assertTrue(
                    outsideBody(helper, spirit),
                    "строитель обязан выйти из укрытия, а стоит в " + spirit.blockPosition());
            helper.assertTrue(
                    count(spirit, Items.JUNGLE_LOG) == 64 && count(spirit, Items.SMOOTH_STONE) == 64,
                    "брёвна и гладкий камень — дорогой материал: они не тратятся на укрытие, осталось "
                            + count(spirit, Items.JUNGLE_LOG) + " и " + count(spirit, Items.SMOOTH_STONE));
            helper.assertTrue(
                    count(spirit, Items.COBBLESTONE) == 2,
                    "булыжника обязано уйти ровно " + BODY_BLOCKS + ", остаток вернулся: осталось "
                            + count(spirit, Items.COBBLESTONE));
            helper.assertTrue(
                    count(spirit, Items.COBBLESTONE_SLAB) == 0 && count(spirit, Items.TORCH) == 0,
                    "полублок порога и факел обязаны быть поставлены: осталось " + count(spirit, Items.COBBLESTONE_SLAB)
                            + " и " + count(spirit, Items.TORCH));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void shelter_ShortMaterial_FailsBeforeFirstBlock(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(
                helper,
                new ItemStack(Items.COBBLESTONE, 10),
                new ItemStack(Items.COBBLESTONE_SLAB, 1),
                new ItemStack(Items.TORCH, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_shelter", Map.of()), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "укрытие без полного запаса обязано провалиться до первого блока, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не хватает") && reason.contains("cobblestone 10/" + BODY_BLOCKS),
                    "причина обязана называть нехватку числами 10 из " + BODY_BLOCKS + ", фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(START).isAir()
                            && helper.getBlockState(START.offset(-3, 0, -2)).isAir(),
                    "до первого блока недоделки нет: стены и клетка духа обязаны остаться воздухом");
            helper.assertTrue(
                    helper.getBlockState(START.below()).is(Blocks.DIRT),
                    "пол не тронут: стройка без полного запаса не начинается, а там "
                            + helper.getBlockState(START.below()).getBlock());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void shelter_NoTorch_FailsBeforeFirstBlock(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(
                helper,
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.COBBLESTONE, 2),
                new ItemStack(Items.COBBLESTONE_SLAB, 1));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_shelter", Map.of()), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "укрытие без факела обязано провалиться до первого блока, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("факел"),
                    "причина обязана называть факел обязательной частью постройки, фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(START).isAir()
                            && helper.getBlockState(START.offset(-3, 0, -2)).isAir(),
                    "без факела ни один блок не ставится: стены и клетка духа обязаны остаться воздухом");
            helper.assertTrue(
                    helper.getBlockState(START.below()).is(Blocks.DIRT),
                    "пол не тронут: без факела стройка не начинается, а там "
                            + helper.getBlockState(START.below()).getBlock());
            GameTestFixtures.finish(helper);
        });
    }
}

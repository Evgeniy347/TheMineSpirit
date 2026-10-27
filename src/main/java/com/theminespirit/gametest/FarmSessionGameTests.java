package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmCropSupport.cropAge;
import static com.theminespirit.gametest.FarmCropSupport.setCrop;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.TILL_SPIRIT;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.assertPerTickBudgets;
import static com.theminespirit.gametest.FarmTestSupport.assertSucceeded;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.changedBlocks;
import static com.theminespirit.gametest.FarmTestSupport.drive;
import static com.theminespirit.gametest.FarmTestSupport.finished;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.launch;
import static com.theminespirit.gametest.FarmTestSupport.reload;
import static com.theminespirit.gametest.FarmTestSupport.savedDone;
import static com.theminespirit.gametest.FarmTestSupport.snapshot;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.neoforge.FarmSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmSessionGameTests {
    private FarmSessionGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void harvestCrop_CancelledMidWork_StopsWorldAndNavigation(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, true));
        AtomicInteger cancelTick = new AtomicInteger(-1);
        AtomicReference<Map<BlockPos, Block>> frozen = new AtomicReference<>();
        AtomicInteger frozenWheat = new AtomicInteger();
        AtomicBoolean navigationStoppedAtCancel = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            if (cancelTick.get() >= 0 || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) >= 1
                    && !spirit.getNavigation().isDone()) {
                spirit.cancelTask();
                navigationStoppedAtCancel.set(spirit.getNavigation().isDone());
                frozen.set(snapshot(helper));
                frozenWheat.set(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT));
                cancelTick.set(spirit.tickCount);
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    cancelTick.get() >= 0 && spirit.tickCount >= cancelTick.get() + 40,
                    "нужно дождаться отмены и переждать 40 тиков: тик отмены " + cancelTick.get() + ", тик духа "
                            + spirit.tickCount);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённая задача обязана быть CANCELLED, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    changedBlocks(helper, frozen.get()) == 0,
                    "через 40 тиков после отмены мир обязан остаться прежним, изменилось блоков "
                            + changedBlocks(helper, frozen.get()));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT) == frozenWheat.get(),
                    "после отмены инвентарь не пополняется");
            helper.assertTrue(
                    navigationStoppedAtCancel.get(),
                    "отмена в движении обязана остановить навигацию сразу, а не дать духу дойти до цели");
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] полный инвентарь, путь до сундука и сбор двух клеток: под нагрузкой машины запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void harvestCrop_InventoryFullNearChest_DepositsAndContinues(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        setCrop(helper, new BlockPos(3, FLOOR_Y, 3), CropKind.WHEAT, 7);
        setCrop(helper, new BlockPos(5, FLOOR_Y, 5), CropKind.WHEAT, 7);
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, true));

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при полном инвентаре дух обязан сдать лишнее на склад и продолжить сбор, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(
                    chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.DIRT) > 0,
                    "лишняя земля обязана уехать в сундук: иначе дух не освободил место и бросил сбор");
            int wheat = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT)
                    + GameTestFixtures.countInWorld(helper, Items.WHEAT)
                    + (chest instanceof Container container
                            ? GameTestFixtures.countInInventory(container, Items.WHEAT)
                            : 0);
            helper.assertTrue(
                    wheat >= 2,
                    "после сдачи урожай обеих клеток обязан собраться (в инвентаре, в мире или в сундуке), фактически "
                            + wheat);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_NearChest_DepositsHarvestAndKeepsSeedReserve(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 24));
        SpiritRun run = launch(helper, spirit, harvestCall(CropKind.WHEAT, false));

        helper.succeedWhen(() -> {
            run.assertStarted();
            assertSucceeded(helper, spirit, "зрелый урожай обязан собраться и уехать на склад");
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(
                    chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.WHEAT) >= cells.size(),
                    "урожай обязан уехать в сундук склада: иначе фермер копит зерно у себя вместо сдачи");
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(
                    seeds == 16,
                    "запас семян на посадку (16) обязан остаться у фермы, а излишек уехать на склад, фактически "
                            + seeds);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void harvestCrop_SpiritReloadedMidWork_KeepsDoneAndFinishes(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        AtomicInteger doneAfterReload = new AtomicInteger(-1);
        launch(helper, spirit, harvestCall(CropKind.WHEAT, true));

        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (GameTestFixtures.countInInventory(current.getInventory(), Items.WHEAT) >= 3) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                SpiritEntity reloaded = reload(helper, current, tag);
                live.set(reloaded);
                helper.runAfterDelay(2, () -> doneAfterReload.set(savedDone(helper, reloaded)));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный сбор обязан дойти до конца: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    doneAfterReload.get() >= 3,
                    "после перезагрузки счётчик done обязан сохраниться (не меньше 3 собранных), а он "
                            + doneAfterReload.get());
            int wheat = GameTestFixtures.countInInventory(current.getInventory(), Items.WHEAT);
            helper.assertTrue(
                    wheat == 9,
                    "каждая клетка собрана ровно раз: пшеницы обязано быть 9, а пересаженное не собрано повторно, "
                            + "фактически " + wheat);
            for (BlockPos cell : cells) {
                helper.assertTrue(cropAge(helper, cell) == 0, "каждая клетка обязана быть пересажена");
            }
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void farmSession_FullHarvestWithReplant_StaysWithinReadAndTickBudgets(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            setCrop(helper, cell, CropKind.WHEAT, 7);
        }
        SpiritEntity spirit = spawnWith(helper, START, new ItemStack(Items.WHEAT_SEEDS, 3));
        AtomicReference<FarmSession> session =
                drive(helper, spirit, ctx -> FarmSession.harvestCrop(ctx, CropKind.WHEAT, 8, true));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(
                    run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "сбор девяти зрелых клеток обязан завершиться успехом: " + run.outcome() + ", "
                            + run.failureReason());
            assertPerTickBudgets(helper, run);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 130)
    public static void farmSession_TillWithWeeds_StaysWithinPerTickBudgets(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, Blocks.DIRT);
            helper.setBlock(cell.above(), Blocks.SHORT_GRASS);
        }
        SpiritEntity spirit = spawnWith(helper, TILL_SPIRIT, new ItemStack(Items.IRON_HOE));
        AtomicReference<FarmSession> session = drive(helper, spirit, ctx -> FarmSession.till(ctx, 4, 9));

        helper.succeedWhen(() -> {
            FarmSession run = finished(helper, session);
            helper.assertTrue(
                    run.outcome() == FarmSession.Outcome.SUCCEEDED,
                    "вспашка девяти клеток под травой обязана завершиться успехом: " + run.outcome() + ", "
                            + run.failureReason());
            helper.assertTrue(countBlock(helper, Blocks.FARMLAND) == 9, "обязано получиться 9 пашни");
            assertPerTickBudgets(helper, run);
            GameTestFixtures.finish(helper);
        });
    }
}

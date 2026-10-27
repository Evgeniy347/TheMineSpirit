package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.REDSTONE_TIMEOUT_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.TOOL_SLOT;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.caveSnapshot;
import static com.theminespirit.gametest.MineSceneSupport.countBlock;
import static com.theminespirit.gametest.MineSceneSupport.dropSummary;
import static com.theminespirit.gametest.MineSceneSupport.mineCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class MineVeinDurabilityGameTests {
    private MineVeinDurabilityGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_CancelledMidWork_LeavesCaveUnchanged(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int z = 0; z <= 4; z++) {
            helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 5), SETTLE_TICKS);
        AtomicInteger cancelTick = new AtomicInteger(-1);
        AtomicReference<String> snapshot = new AtomicReference<>();

        helper.onEachTick(() -> {
            if (cancelTick.get() >= 0 || spirit.tickCount < SETTLE_TICKS) {
                return;
            }
            if (GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) >= 1) {
                spirit.cancelTask();
                snapshot.set(caveSnapshot(helper, CAVE_SHIFT_INSIDE_ARENA));
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
                    caveSnapshot(helper, CAVE_SHIFT_INSIDE_ARENA).equals(snapshot.get()),
                    "через 40 тиков после отмены мир обязан остаться прежним");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_PickaxeWithOneDurability_FailsBeforeMiningAsNeedTool(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.IRON_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        ItemStack pickaxe = spirit.getInventory().getItem(TOOL_SLOT);
        int lastUseDamage = pickaxe.getMaxDamage() - 1;
        pickaxe.setDamageValue(lastUseDamage);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "изношенная кирка обязана провалить задачу до первой руды, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("нужен инструмент") && reason.contains("кирка"),
                    "причина обязана назвать, что нужен инструмент (кирка), фактически: " + reason);
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "первая руда обязана остаться: последний удар изношенной кирки не тратится");
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.IRON_ORE),
                    "вторая руда обязана остаться: её нечем добыть");
            helper.assertTrue(
                    spirit.getInventory().getItem(TOOL_SLOT).getDamageValue() == lastUseDamage,
                    "изношенная кирка не должна изнашиваться дальше, фактический износ "
                            + spirit.getInventory().getItem(TOOL_SLOT).getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_AnyOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("any", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: задача обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("без подходящего инструмента: 1"),
                    "причина обязана посчитать пропущенный алмаз, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.DIAMOND_ORE),
                    "алмазная руда обязана остаться целой");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void mineVein_TwoRedstoneOre_ConservesAllDrops(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.REDSTONE_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.REDSTONE_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("redstone", 2), SETTLE_TICKS);
        HarvestProbe probe = HarvestProbe.attach(
                helper,
                spirit,
                run::started,
                () -> GameTestFixtures.countInInventory(spirit.getInventory(), Items.REDSTONE),
                () -> GameTestFixtures.countInWorld(helper, Items.REDSTONE),
                () -> dropSummary(helper, Items.REDSTONE),
                () -> countBlock(helper, Blocks.REDSTONE_ORE, CAVE_SHIFT_INSIDE_ARENA));
        helper.onEachTick(() -> {
            if (run.started()
                    && spirit.taskStatus().orElse(null) != RunStatus.SUCCEEDED
                    && helper.getTick() >= REDSTONE_TIMEOUT_TICKS) {
                helper.fail(probe.report("тайм-аут " + REDSTONE_TIMEOUT_TICKS + " тиков игры"));
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "две редстоуновые руды обязаны добыться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет") + "\n" + probe.report("нет успеха"));
            int inInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.REDSTONE);
            int inWorld = GameTestFixtures.countInWorld(helper, Items.REDSTONE);
            int total = inInventory + inWorld;
            helper.assertTrue(
                    total >= 8 && total <= 10,
                    "две руды дают 8..10 редстоуна вместе в инвентаре и мире, в инвентаре " + inInventory
                            + ", в мире " + inWorld + ", фактически " + total + "\n"
                            + probe.report("дроп не сошёлся"));
            GameTestFixtures.finish(helper);
        });
    }

    // [флак] полный инвентарь, путь до сундука и добыча руды: под нагрузкой машины запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void mineVein_FullInventoryNearChest_DepositsAndContinues(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        for (int slot = 1; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.IRON_ORE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при полном инвентаре дух обязан сдать лишнее на склад и продолжить добычу, статус "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 1,
                    "после сдачи руда обязана оказаться в инвентаре: добыча продолжилась, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(
                    chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.DIRT) > 0,
                    "лишняя земля обязана уехать в сундук: иначе дух не освободил место и бросил задачу");
            GameTestFixtures.finish(helper);
        });
    }
}

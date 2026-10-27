package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.RADIUS;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_CORNER;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.gatherCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.HarvestSession;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class GatherRulesGameTests {
    private GatherRulesGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_FewTargetsLeft_KeepsRefillingSearchEachTick(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        for (int x = 1; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(x + CAVE_SHIFT_INSIDE_ARENA, 1, z + CAVE_SHIFT_INSIDE_ARENA, Blocks.COBBLESTONE);
            }
        }
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);
        AtomicReference<HarvestSession> session = new AtomicReference<>();
        AtomicInteger slicesAtFirstFind = new AtomicInteger(-1);
        AtomicBoolean refilledWhileSearching = new AtomicBoolean(false);

        helper.onEachTick(() -> {
            HarvestSession run = session.get();
            if (run == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(HarvestSession.gather(spirit, "cobblestone", 6, RADIUS));
                }
                return;
            }
            if (run.outcome() == HarvestSession.Outcome.RUNNING) {
                run.tick();
                if (run.mined() == 0) {
                    if (slicesAtFirstFind.get() < 0) {
                        slicesAtFirstFind.set(run.slicesUsed());
                    } else if (run.slicesUsed() > slicesAtFirstFind.get()) {
                        refilledWhileSearching.set(true);
                    }
                }
            }
        });

        helper.succeedWhen(() -> {
            HarvestSession run = session.get();
            helper.assertTrue(run != null, "сессия обязана стартовать");
            helper.assertTrue(
                    run.outcome() != HarvestSession.Outcome.RUNNING, "сессия обязана завершиться, а висит в RUNNING");
            helper.assertTrue(
                    refilledWhileSearching.get(),
                    "при остатке меньше 8 целей и незаконченном поиске сессия обязана за тик дочитать новую порцию: "
                            + "иначе правило «осталось меньше восьми — искать ещё» мёртвое; срезов у первого поиска "
                            + slicesAtFirstFind.get() + ", всего " + run.slicesUsed());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void gather_WildcardLogFilter_CollectsAllMatchingLogs(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(1 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.OAK_LOG);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_AXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_log", 3), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "три бревна по шаблону *_log обязаны собраться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 3,
                    "в инвентаре обязано быть 3 бревна, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void gather_ForbiddenContainerAndBedrock_RefuseAtOnceAndKeepChest(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA, Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(
                        helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA)));
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.BEDROCK);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.IRON_PICKAXE);

        HarvestSession chestSession = HarvestSession.gather(spirit, "minecraft:chest", 1, RADIUS);
        helper.assertTrue(
                chestSession.tick() == HarvestSession.Outcome.FAILED,
                "сундук запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(
                chestSession.failureReason().contains("запрещён"),
                "причина обязана объяснять запрет, фактически: " + chestSession.failureReason());

        HarvestSession bedrockSession = HarvestSession.gather(spirit, "minecraft:bedrock", 1, RADIUS);
        helper.assertTrue(
                bedrockSession.tick() == HarvestSession.Outcome.FAILED,
                "бедрок запрещён для gather: отказ обязан наступить сразу");
        helper.assertTrue(
                helper.getBlockState(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA))
                        .is(Blocks.CHEST),
                "запрещённый сундук обязан остаться на месте");
        ChestBlockEntity kept = (ChestBlockEntity) helper.getLevel()
                .getBlockEntity(
                        helper.absolutePos(new BlockPos(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 2 + CAVE_SHIFT_INSIDE_ARENA)));
        helper.assertTrue(
                kept.getItem(0).is(Items.DIAMOND) && kept.getItem(0).getCount() == 3,
                "содержимое сундука обязано уцелеть, в слоте " + kept.getItem(0));
        GameTestFixtures.reset(helper);
        helper.succeed();
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void gather_WildcardOreWithStonePickaxe_MinesCoalAndSkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    !reason.contains("сломался"),
                    "каменная кирка цела: причина не смеет говорить о поломке, фактически: " + reason);
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
    public static void gather_WildcardOreDiamondCloserThanCoal_SkipsDiamond(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA, Blocks.DIAMOND_ORE);
        helper.setBlock(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.COAL_ORE);
        SpiritEntity spirit = spawnWith(
                helper, SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA), Items.STONE_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("*_ore", 2), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "алмаз не по кирке: gather обязан провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("без подходящего инструмента: 1"),
                    "первым выбран ближний алмаз без подходящего инструмента: его обязано пропустить и посчитать, "
                            + "а не отказываться до начала, фактически: " + reason);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL) == 1,
                    "дальний уголь обязан быть добыт каменной киркой, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COAL));
            helper.assertTrue(
                    helper.getBlockState(new BlockPos(-1 + CAVE_SHIFT_INSIDE_ARENA, 2, 1 + CAVE_SHIFT_INSIDE_ARENA))
                            .is(Blocks.DIAMOND_ORE),
                    "ближний алмаз обязан остаться целой");
            GameTestFixtures.finish(helper);
        });
    }
}

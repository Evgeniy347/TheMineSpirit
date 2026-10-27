package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.ARENA_START;
import static com.theminespirit.gametest.MineSceneSupport.CAVE_SHIFT_INSIDE_ARENA;
import static com.theminespirit.gametest.MineSceneSupport.RADIUS;
import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_CORNER;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.UNPICKABLE_DELAY;
import static com.theminespirit.gametest.MineSceneSupport.UNPICKABLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.buildCave;
import static com.theminespirit.gametest.MineSceneSupport.countBlock;
import static com.theminespirit.gametest.MineSceneSupport.gatherCall;
import static com.theminespirit.gametest.MineSceneSupport.reload;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.neoforge.BlockLocator;
import com.theminespirit.neoforge.BlockSets;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class GatherProgressGameTests {
    private GatherProgressGameTests() {}

    // [флак] 2400 тиков общего подбора при задержке подъёма дропа и разлёте: запас 2× к ожиданию
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void gather_StoneDropTemporarilyUnpickable_CollectsAfterDelay(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        helper.setBlock(2 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        helper.setBlock(3 + CAVE_SHIFT_INSIDE_ARENA, 1, 3 + CAVE_SHIFT_INSIDE_ARENA, Blocks.STONE);
        SpiritEntity spirit = spawnWith(
                helper,
                SPIRIT_CORNER.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA),
                Items.WOODEN_PICKAXE);
        SpiritRun run = SpiritRun.launch(helper, spirit, gatherCall("stone", 2), SETTLE_TICKS);

        int[] dropTick = {-1};
        helper.onEachTick(() -> {
            List<ItemEntity> drops = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            if (drops.isEmpty()) {
                return;
            }
            if (dropTick[0] < 0) {
                dropTick[0] = spirit.tickCount;
            }
            for (ItemEntity drop : drops) {
                if (spirit.tickCount < dropTick[0] + UNPICKABLE_TICKS) {
                    drop.setPickUpDelay(UNPICKABLE_DELAY);
                } else {
                    drop.setNoPickUpDelay();
                }
            }
        });

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "временно не поднимаемый дроп не валит добычу, пока есть недобытые блоки: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "оба булыжника обязаны попасть в инвентарь после снятия задержки, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 280)
    public static void gatherCobblestone_SavedMidway_ResumesWithoutExtraMining(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, ARENA_START, Items.IRON_PICKAXE);
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        SpiritRun.launch(helper, spirit, gatherCall("cobblestone", 6), SETTLE_TICKS);
        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE) == 3) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                live.set(reload(helper, current, tag));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "восстановленный gather обязан досчитать до 6 блоков: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет") + ", тиков духа " + current.tickCount);
            int inInventory = GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    inInventory == 6,
                    "после перезагрузки на середине в инвентаре обязано быть ровно 6 булыжника, а не пересчёт "
                            + "с нуля: " + inInventory);
            helper.assertTrue(
                    countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "в куче обязано остаться " + (heapBefore - 6) + " блоков (исходно " + heapBefore
                            + " минус 6), а не " + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 260)
    public static void gatherCobblestone_SavedAfterLastBlock_RestoresAsSucceeded(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 4; z <= 5; z++) {
                helper.setBlock(x, 1, z, Blocks.COBBLESTONE);
            }
        }
        int heapBefore = countBlock(helper, Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, ARENA_START, Items.IRON_PICKAXE);
        AtomicReference<SpiritEntity> live = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        SpiritRun.launch(helper, spirit, gatherCall("cobblestone", 6), SETTLE_TICKS);
        helper.onEachTick(() -> {
            if (saved.get()) {
                return;
            }
            SpiritEntity current = live.get();
            if (current.taskStatus().orElse(null) == RunStatus.RUNNING
                    && countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                current.saveWithoutId(tag);
                live.set(reload(helper, current, tag));
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = live.get();
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "снимок в момент сбора последнего блока обязан восстановиться успехом, а не искать "
                            + "седьмой блок или падать «добыто 6 из 6»: статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            helper.assertTrue(saved.get(), "снимок обязан быть сделан после шестого блока");
            helper.assertTrue(
                    countBlock(helper, Blocks.COBBLESTONE) == heapBefore - 6,
                    "после восстановления нельзя добывать ещё: в куче обязано остаться " + (heapBefore - 6) + ", стало "
                            + countBlock(helper, Blocks.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 100)
    public static void blockLocator_TwoStepsInOneTick_CountsSlicesPerTick(GameTestHelper helper) {
        buildCave(helper, CAVE_SHIFT_INSIDE_ARENA);
        BlockPos center = SPIRIT_START.offset(CAVE_SHIFT_INSIDE_ARENA, 0, CAVE_SHIFT_INSIDE_ARENA);
        BlockLocator.Search search = BlockLocator.search(
                helper.getLevel(),
                helper.absolutePos(center),
                RADIUS,
                BlockSets.resolve(IdFilter.of(List.of("minecraft:stone"))));

        search.step(1);
        int afterFirst = search.maxSlicesPerTick();
        search.step(2);
        int afterSecond = search.maxSlicesPerTick();

        helper.assertTrue(afterFirst == 1, "первый шаг в тик обязан посчитать один срез, а посчитал " + afterFirst);
        helper.assertTrue(
                afterSecond == 3,
                "второй шаг в том же тике обязан прибавить свои срезы к тику (1 + 2 = 3), а максимум стал "
                        + afterSecond);
        GameTestFixtures.reset(helper);
        helper.succeed();
    }
}

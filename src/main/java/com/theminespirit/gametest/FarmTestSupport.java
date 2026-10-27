package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.crop;
import static com.theminespirit.gametest.FarmTestSupport.ARENA_HEIGHT;
import static com.theminespirit.gametest.FarmTestSupport.ARENA_SIZE;
import static com.theminespirit.gametest.FarmTestSupport.FLOOR_Y;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.FarmSession;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmTestSupport {
    static final int GROUND_Y = 1;
    static final int FLOOR_Y = 0;
    static final int SETTLE_TICKS = 5;
    static final int ARENA_SIZE = 11;
    static final int ARENA_HEIGHT = 8;
    static final int CROP_MIN_LIGHT = 8;
    static final int FALL_SETTLE_TICKS = 30;
    static final BlockPos START = new BlockPos(1, 1, 1);
    static final BlockPos TILL_SPIRIT = new BlockPos(5, 1, 7);
    static final BlockPos WATER = new BlockPos(1, FLOOR_Y, 4);

    private FarmTestSupport() {}

    static void assertPerTickBudgets(GameTestHelper helper, FarmSession run) {
        helper.assertTrue(
                run.maxSlicesPerTick() <= 4,
                "за тик поиск берёт не больше 4 срезов, максимум за тик: " + run.maxSlicesPerTick());
        helper.assertTrue(
                run.maxActionsPerTick() <= 1,
                "за тик скрипт делает не больше одного действия (удар, вспашка, посадка, мука), максимум за тик: "
                        + run.maxActionsPerTick());
        helper.assertTrue(
                run.maxEntityQueriesPerTick() <= 1,
                "за тик запрос сущностей не больше одного, максимум за тик: " + run.maxEntityQueriesPerTick());
    }

    static int savedDone(GameTestHelper helper, SpiritEntity spirit) {
        Optional<String> json = GameTestFixtures.taskSnapshot(spirit);
        helper.assertTrue(json.isPresent(), "у восстановленного духа обязан быть снимок задачи");
        TaskProgress progress = TaskSnapshotCodec.decode(json.get()).progress();
        helper.assertTrue(
                progress instanceof TaskProgress.ScriptAt,
                "снимок одиночного скрипта обязан нести состояние скрипта, а там " + progress);
        Object done = ((TaskProgress.ScriptAt) progress).state().get("done");
        helper.assertTrue(done instanceof Number, "в состоянии скрипта обязан быть счётчик done, а там " + done);
        return ((Number) done).intValue();
    }

    static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        if (!helper.getLevel().addFreshEntity(reloaded)) {
            throw new IllegalStateException("перезагруженный дух не добавлен в мир: uuid " + reloaded.getUUID());
        }
        return reloaded;
    }

    static int brightnessAbove(GameTestHelper helper, BlockPos ground) {
        return helper.getLevel().getRawBrightness(helper.absolutePos(ground.above()), 0);
    }

    static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    static void fillGround(GameTestHelper helper, Block block) {
        for (BlockPos cell : bed()) {
            helper.setBlock(cell, block);
        }
    }

    static List<BlockPos> bed() {
        List<BlockPos> cells = new ArrayList<>();
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                cells.add(new BlockPos(x, FLOOR_Y, z));
            }
        }
        return cells;
    }

    static SpiritRun launch(GameTestHelper helper, SpiritEntity spirit, TaskNode call) {
        return SpiritRun.launch(helper, spirit, call, SETTLE_TICKS);
    }

    static void assertSucceeded(GameTestHelper helper, SpiritEntity spirit, String expectation) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                expectation + ": статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                        + spirit.taskFailureReason().orElse("нет"));
    }

    static void assertFailedWith(GameTestHelper helper, SpiritEntity spirit, String fragment) {
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                "задача обязана провалиться, а статус "
                        + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        String reason = spirit.taskFailureReason().orElse("");
        helper.assertTrue(
                reason.contains(fragment),
                "причина провала обязана содержать «" + fragment + "», фактически: " + reason);
    }

    static ScriptCall plantCall(CropKind crop, int count) {
        return new ScriptCall("plant_crop", Map.of("crop", name(crop), "radius", 8, "count", count));
    }

    static ScriptCall harvestCall(CropKind crop, boolean replant) {
        return new ScriptCall("harvest_crop", Map.of("crop", name(crop), "radius", 8, "replant", replant));
    }

    static ScriptCall boneMealCall(CropKind crop, int count) {
        return new ScriptCall("bone_meal", Map.of("crop", name(crop), "radius", 8, "count", count));
    }

    static String name(CropKind crop) {
        return crop.name().toLowerCase(Locale.ROOT);
    }

    static ScriptCall tillCall(int radius, int count) {
        return new ScriptCall("till", Map.of("radius", radius, "count", count));
    }

    static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack... stacks) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        for (int slot = 0; slot < stacks.length; slot++) {
            spirit.getInventory().setItem(slot, stacks[slot]);
        }
        return spirit;
    }

    static AtomicReference<FarmSession> drive(
            GameTestHelper helper, SpiritEntity spirit, Function<SpiritEntity, FarmSession> factory) {
        AtomicReference<FarmSession> session = new AtomicReference<>();
        helper.onEachTick(() -> {
            FarmSession current = session.get();
            if (current == null) {
                if (spirit.tickCount >= SETTLE_TICKS) {
                    session.set(factory.apply(spirit));
                }
                return;
            }
            if (current.outcome() == FarmSession.Outcome.RUNNING) {
                current.tick();
            }
        });
        return session;
    }

    static FarmSession finished(GameTestHelper helper, AtomicReference<FarmSession> session) {
        FarmSession run = session.get();
        helper.assertTrue(
                run != null && run.outcome() != FarmSession.Outcome.RUNNING,
                "сессия обязана завершиться, а не висеть в RUNNING: "
                        + (run == null ? "сессия не стартовала" : run.outcome().name()));
        return run;
    }

    static Map<BlockPos, Block> snapshot(GameTestHelper helper) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int y = 0; y < ARENA_HEIGHT; y++) {
                for (int z = 0; z < ARENA_SIZE; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    blocks.put(pos, helper.getBlockState(pos).getBlock());
                }
            }
        }
        return blocks;
    }

    static int changedBlocks(GameTestHelper helper, Map<BlockPos, Block> before) {
        int changed = 0;
        for (Map.Entry<BlockPos, Block> entry : before.entrySet()) {
            if (!helper.getBlockState(entry.getKey()).is(entry.getValue())) {
                changed++;
            }
        }
        return changed;
    }
}

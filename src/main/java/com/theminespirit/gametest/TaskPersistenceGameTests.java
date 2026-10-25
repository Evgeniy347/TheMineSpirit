package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTaskStore;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TaskPersistenceGameTests {
    private TaskPersistenceGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void savedRunningSequence_ResumesAtSecondStep_AndReachesTarget(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 1));
        spirit.startTask(new SequenceNode(List.of(
                new ScriptCall("goto", Map.of("x", first.getX(), "y", first.getY(), "z", first.getZ())),
                new ScriptCall("goto", Map.of("x", second.getX(), "y", second.getY(), "z", second.getZ())))));

        helper.runAfterDelay(1, () -> {
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            SpiritEntity reloaded = reload(helper, spirit, tag);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(
                        reloaded.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "восстановленный дух обязан сразу иметь RUNNING: статус "
                                + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                TaskSnapshot snapshot = snapshotOf(helper, reloaded);
                TaskProgress progress = snapshot.progress();
                helper.assertTrue(
                        progress instanceof TaskProgress.SequenceAt at && at.index() == 1,
                        "снимок обязан показывать шаг 1 (goto P2): повтор шага 0 означал бы, что позиция sequence "
                                + "потеряна и дух заново идёт к P1. Прогресс: " + progress);
                helper.runAfterDelay(40, () -> {
                    double distance = reloaded.position()
                            .distanceTo(new Vec3(second.getX() + 0.5, second.getY(), second.getZ() + 0.5));
                    helper.assertTrue(
                            distance <= 1.5,
                            "восстановленный goto P2 обязан дойти до цели: расстояние " + distance + ", статус "
                                    + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                    helper.assertTrue(
                            reloaded.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                            "дойдя до цели, дух обязан остаться с SUCCEEDED, а не перезапускать сохранённую задачу "
                                    + "заново: статус "
                                    + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                    helper.succeed();
                });
            });
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void savedCancelledTask_AfterReload_StaysCancelledAndStill(GameTestHelper helper) {
        buildFloor(helper);
        SpiritEntity spirit = loadTask(helper, terminalJson(RunStatus.CANCELLED, null), new BlockPos(1, 1, 1));
        Vec3 before = spirit.position();

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "отменённая задача обязана остаться CANCELLED после загрузки, а не оживать: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.position().equals(before),
                    "отменённая задача не должна двигать духа: был " + before + ", стал " + spirit.position());
            helper.succeed();
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 120)
    public static void savedFailedTask_AfterReload_KeepsReasonVerbatim(GameTestHelper helper) {
        String reason = "цель недостижима: провал записан дословно";
        SpiritEntity spirit = loadTask(helper, terminalJson(RunStatus.FAILED, reason), new BlockPos(1, 1, 1));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "провал обязан сохраниться как FAILED, а не превратиться в другой статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").equals(reason),
                    "причина провала обязана пережить загрузку дословно. Ожидалось '" + reason + "', получено '"
                            + spirit.taskFailureReason().orElse("нет причины") + "'");
            helper.succeed();
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void corruptedSpiritTask_AfterReload_MarksFailedAndStaysAlive(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity spirit = loadTask(helper, "{это не json", new BlockPos(1, 1, 1));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    spirit.isAlive(), "битый SpiritTask не должен ронять сущность: дух обязан остаться живым");
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "битый SpiritTask обязан стать FAILED с понятной причиной: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").startsWith("не удалось восстановить задачу"),
                    "причина обязана начинаться с 'не удалось восстановить задачу', а получено '"
                            + spirit.taskFailureReason().orElse("нет причины") + "'");
            helper.succeed();
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 120)
    public static void futureVersionSpiritTask_AfterReload_MentionsVersionInReason(GameTestHelper helper) {
        String json = "{\"version\":999,\"status\":\"SUCCEEDED\"}";
        SpiritEntity spirit = loadTask(helper, json, new BlockPos(1, 1, 1));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "снимок будущей версии обязан стать FAILED, а не молча пропасть: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("999"),
                    "причина обязана называть неподдерживаемую версию 999, получено '"
                            + spirit.taskFailureReason().orElse("нет причины") + "'");
            helper.succeed();
        });
    }

    @SceneStep(template = "empty", timeoutTicks = 120)
    public static void unknownScriptInTask_AfterReload_MentionsScriptInReason(GameTestHelper helper) {
        TaskSnapshot snapshot = new TaskSnapshot(
                TaskSnapshot.VERSION,
                RunStatus.RUNNING,
                null,
                new ScriptCall("нет_такого_скрипта", Map.of()),
                new TaskProgress.ScriptAt(Map.of()));
        SpiritEntity spirit = loadTask(helper, TaskSnapshotCodec.encode(snapshot), new BlockPos(1, 1, 1));

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "план со скриптом, которого нет в реестре, обязан стать FAILED: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("нет_такого_скрипта"),
                    "причина обязана называть отсутствующий скрипт, получено '"
                            + spirit.taskFailureReason().orElse("нет причины") + "'");
            helper.succeed();
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void legacySpiritWithoutTaskKey_AfterReload_HasNoTaskAndKeepsInventory(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritEntity reloaded = reload(helper, spirit, tag);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    reloaded.taskStatus().isEmpty(),
                    "старый дух без ключа SpiritTask обязан загрузиться без задачи: статус "
                            + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(reloaded.getInventory(), Items.DIAMOND) == 3,
                    "инвентарь старого духа обязан пережить загрузку: алмазов "
                            + GameTestFixtures.countInInventory(reloaded.getInventory(), Items.DIAMOND));
            helper.succeed();
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void savedTaskAndInventory_AfterReload_KeepsBoth(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        TaskSnapshot snapshot = new TaskSnapshot(
                TaskSnapshot.VERSION,
                RunStatus.RUNNING,
                null,
                new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())),
                new TaskProgress.ScriptAt(Map.of()));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritTaskStore.write(tag, TaskSnapshotCodec.encode(snapshot));
        SpiritEntity reloaded = reload(helper, spirit, tag);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(
                    GameTestFixtures.countInInventory(reloaded.getInventory(), Items.DIAMOND) == 3,
                    "инвентарь и задача сохраняются вместе: алмазов "
                            + GameTestFixtures.countInInventory(reloaded.getInventory(), Items.DIAMOND));
            helper.assertTrue(
                    reloaded.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "задача обязана восстановиться вместе с инвентарём: статус "
                            + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.succeed();
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void saveWhileRunning_DoesNotDisturbOriginalTask_UntilItReachesTarget(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        spirit.startTask(new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())));

        helper.runAfterDelay(10, () -> {
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.RUNNING,
                    "сохранение не должно останавливать работающую задачу: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        });
        helper.succeedWhen(() -> {
            double distance =
                    spirit.position().distanceTo(new Vec3(target.getX() + 0.5, target.getY(), target.getZ() + 0.5));
            helper.assertTrue(
                    distance <= 1.5,
                    "оригинальный дух после сохранения обязан дойти до цели: расстояние " + distance + ", статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void runningTask_ForHundredTicks_BuildsNoSnapshot(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        BlockPos far = helper.absolutePos(new BlockPos(2, 1, 1));
        spirit.startTask(new ScriptCall("goto", Map.of("x", far.getX(), "y", far.getY(), "z", far.getZ())));

        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                    spirit.snapshotBuilds() == 0,
                    "за 100 тиков работы задачи снимок не строится ни разу: счётчик " + spirit.snapshotBuilds()
                            + ". Снимок — только при сохранении сущности, иначе тик дорожает");
            helper.succeed();
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 120)
    public static void saveBeforeFirstTick_OnLiveSpirit_KeepsTaskBytesUnchanged(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        TaskSnapshot snapshot = new TaskSnapshot(
                TaskSnapshot.VERSION,
                RunStatus.RUNNING,
                null,
                new ScriptCall("goto", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ())),
                new TaskProgress.ScriptAt(Map.of()));
        String original = TaskSnapshotCodec.encode(snapshot);

        CompoundTag loaded = new CompoundTag();
        SpiritTaskStore.write(loaded, original);
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(helper.getLevel());
        spirit.load(loaded);

        CompoundTag saved = new CompoundTag();
        spirit.saveWithoutId(saved);
        spirit.discard();

        helper.assertTrue(
                original.equals(SpiritTaskStore.read(saved).orElse(null)),
                "сохранение до первого тика обязано вернуть исходную строку SpiritTask без изменений: иначе "
                        + "задача и итог пропадут навсегда. Было '" + original + "', стало '"
                        + SpiritTaskStore.read(saved).orElse("нет задачи") + "'");
        helper.succeed();
    }

    private static TaskSnapshot snapshotOf(GameTestHelper helper, SpiritEntity spirit) {
        Optional<String> json = GameTestFixtures.taskSnapshot(spirit);
        helper.assertTrue(json.isPresent(), "у восстановленного духа обязан быть снимок задачи");
        return TaskSnapshotCodec.decode(json.get());
    }

    private static SpiritEntity loadTask(GameTestHelper helper, String json, BlockPos pos) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), pos);
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritTaskStore.write(tag, json);
        return reload(helper, spirit, tag);
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    private static String terminalJson(RunStatus status, String reason) {
        return TaskSnapshotCodec.encode(new TaskSnapshot(TaskSnapshot.VERSION, status, reason, null, null));
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}

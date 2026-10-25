package com.theminespirit.gametest;

import static com.theminespirit.gametest.CraftTestSupport.assertCount;
import static com.theminespirit.gametest.CraftTestSupport.cleanup;
import static com.theminespirit.gametest.CraftTestSupport.field;
import static com.theminespirit.gametest.CraftTestSupport.prepare;
import static com.theminespirit.gametest.FullCycleScene.TABLE;
import static com.theminespirit.gametest.FullCycleScene.TREE;
import static com.theminespirit.gametest.FullCycleScene.TREE_LOGS;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AcquireBrainGameTests {
    private static final int SNAPSHOT_DELAY = 5;

    private AcquireBrainGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void acquireRunning_SnapshotAndRestore_ContinuesToSuccess(GameTestHelper helper) {
        field(helper);
        FullCycleScene.buildOakTree(helper, TREE, TREE_LOGS);
        helper.setBlock(TABLE, Blocks.CRAFTING_TABLE);
        SpiritEntity spirit = prepare(helper);
        spirit.startTask(new ScriptCall("acquire", Map.of("item", "wooden_pickaxe", "count", 1)));
        AtomicReference<SpiritEntity> restored = new AtomicReference<>();

        helper.runAfterDelay(SNAPSHOT_DELAY, () -> {
            CompoundTag tag = new CompoundTag();
            spirit.saveWithoutId(tag);
            SpiritEntity reloaded = CraftTestSupport.reload(helper, spirit);
            restored.set(reloaded);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(
                        reloaded.taskStatus().orElse(null) == RunStatus.RUNNING,
                        "восстановленный acquire обязан продолжить с того же шага: статус "
                                + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи"));
                helper.assertTrue(
                        GameTestFixtures.taskSnapshot(reloaded).isPresent(),
                        "снимок вложенного раннера обязан пережить сохранение: задача восстановлена без снимка");
                String snapshot = GameTestFixtures.taskSnapshot(reloaded).orElse("");
                helper.assertTrue(
                        snapshot.contains("attempts") || snapshot.contains("runner"),
                        "снимок обязан хранить состояние acquire (attempts/runner): " + snapshot);
            });
        });

        helper.succeedWhen(() -> {
            SpiritEntity reloaded = restored.get();
            helper.assertTrue(reloaded != null, "дух не был перезагружен: сохранение не выполнено");
            RunStatus status = reloaded.taskStatus().orElse(null);
            helper.assertTrue(
                    status == RunStatus.SUCCEEDED,
                    "задача обязана дойти до SUCCEEDED после перезапуска, статус: "
                            + (status == null ? "нет задачи" : status)
                            + ", причина: " + reloaded.taskFailureReason().orElse("нет"));
            assertCount(helper, reloaded, Items.WOODEN_PICKAXE, 1, "деревянная кирка после перезапуска");
            helper.assertTrue(
                    FullCycleScene.countBlocks(helper, Blocks.OAK_LOG) == TREE_LOGS - 2,
                    "подплан берёт 2 брёвна из 3 (верстак уже стоит, второй не строится), дерево не должно "
                            + "израсходовываться дважды, осталось "
                            + FullCycleScene.countBlocks(helper, Blocks.OAK_LOG));
            cleanup(helper, reloaded);
        });
    }
}

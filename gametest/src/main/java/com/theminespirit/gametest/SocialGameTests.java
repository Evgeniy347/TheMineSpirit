package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocialGameTests {
    private SocialGameTests() {}

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deliver_SavedMidRoute_DeliversExactlyOnce(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "RestartTarget");
        AtomicReference<SpiritEntity> holder = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("deliver", Map.of("item", "cobblestone", "count", 3, "to", "player:RestartTarget")),
                10);
        helper.onEachTick(() -> {
            if (saved.get() || !spirit.equals(holder.get())) {
                return;
            }
            if (spirit.taskStatus().orElse(null) == RunStatus.RUNNING && run.spiritTicks() >= 8) {
                saved.set(true);
                CompoundTag tag = new CompoundTag();
                spirit.saveWithoutId(tag);
                SpiritEntity reloaded = reload(helper, spirit, tag);
                holder.set(reloaded);
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = holder.get();
            run.assertStarted();
            helper.assertTrue(saved.get(), "перезапуск по дороге ещё не выполнен");
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deliver после перезапуска обязан довести предмет, статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            int withPlayer = GameTestFixtures.countInInventory(player.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    withPlayer == 3,
                    "предмет обязан быть передан ровно один раз: у игрока " + withPlayer + " булыжников");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE) == 0,
                    "у духа не остаётся булыжников, осталось "
                            + GameTestFixtures.countInInventory(current.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    current.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(3).equals(data.fields().get("delivered")),
                    "итог deliver после перезапуска обязан назвать delivered=3, получено "
                            + current.taskResult().map(Object::toString).orElse("пусто"));
            TacticsScene.finish(helper, current);
        });
    }
}

package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritTaskStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocialGameTests {
    private SocialGameTests() {}

    @GameTest(template = "arena", batch = "social_deliver", timeoutTicks = 300)
    public static void deliver_ThreeCobbleToPlayer_PlayerGetsAll(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "DeliverTarget");

        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("deliver", Map.of("item", "cobblestone", "count", 3, "to", "player:DeliverTarget")),
                10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deliver обязан довести предмет и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int withPlayer = GameTestFixtures.countInInventory(player.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(withPlayer == 3, "адресат обязан получить ровно 3 булыжника, у игрока: " + withPlayer);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "после доставки у духа не должно остаться булыжников, осталось: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_deliver_missing", timeoutTicks = 150)
    public static void deliver_NoItem_FailsWithNumbers(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "DeliverEmpty");

        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("deliver", Map.of("item", "cobblestone", "count", 3, "to", "player:DeliverEmpty")),
                10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "deliver без предмета обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не хватает"), "отказ обязан называть нехватку предмета, причина: " + reason);
            helper.assertTrue(
                    reason.contains("3") && reason.contains("0"),
                    "отказ обязан содержать числа (сколько нужно и сколько есть), причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_meet", timeoutTicks = 300)
    public static void meet_PlayerNearby_SpiritArrives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(9, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 5), "MeetTarget");

        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("meet", Map.of("target", "player:MeetTarget")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "meet обязан подвести духа к игроку и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = spirit.position().distanceTo(player.position());
            helper.assertTrue(
                    distance <= SocialTuning.MEET_ARRIVAL + 0.5,
                    "дух обязан остановиться на MEET_ARRIVAL (" + SocialTuning.MEET_ARRIVAL
                            + ") от игрока, расстояние: " + distance);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_meet_missing", timeoutTicks = 100)
    public static void meet_TargetMissing_FailsWithText(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("meet", Map.of("target", "player:NoSuchPlayer")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "meet без цели обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("не найдена"), "отказ обязан называть ненайденную цель, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_say_wait", timeoutTicks = 200)
    public static void say_ShortText_PlayerGetsMessage(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        List<Component> messages = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(3, 1, 1), "SayTarget", messages);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("say", Map.of("text", "привет")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "say обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    messages.stream().anyMatch(m -> m.getString().contains("привет")),
                    "игрок обязан получить сообщение с текстом, сообщения: " + messages);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_say_radius", timeoutTicks = 200)
    public static void say_ShortText_OnlyPlayersInsideHearingRadiusGetIt(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        List<Component> near = new ArrayList<>();
        List<Component> far = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(3, 1, 1), "NearSay", near);
        GameTestFixtures.mockPlayer(
                helper, new BlockPos(3 + CatalogLimits.SAY_HEARING_RADIUS + 4, 1, 1), "FarSay", far);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("say", Map.of("text", "слышно")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "say обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    near.stream().anyMatch(m -> m.getString().contains("слышно")),
                    "игрок в двух блоках обязан услышать духа, сообщения: " + near);
            helper.assertTrue(
                    far.stream().noneMatch(m -> m.getString().contains("слышно")),
                    "игрок дальше " + CatalogLimits.SAY_HEARING_RADIUS + " блоков не должен слышать духа, сообщения: "
                            + far);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_say_spam", timeoutTicks = 100)
    public static void say_SecondSayImmediately_FailsWithAntiSpam(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        List<Component> messages = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(3, 1, 1), "SpamTarget", messages);

        spirit.setLastSayTick(spirit.tickCount);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("say", Map.of("text", "спам")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "второй say сразу обязан отказаться по анти-спаму, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("спам") || reason.contains("интервал"),
                    "отказ обязан называть анти-спам, причина: " + reason);
            helper.assertTrue(reason.contains("20"), "отказ обязан содержать число интервала (20), причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_wait", timeoutTicks = 200)
    public static void wait_FortyTicks_SucceedsAfterSpiritTicks(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("wait", Map.of("ticks", 40)), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "wait 40 тиков обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    run.spiritTicks() >= 40, "wait обязан ждать ровно 40 тиков духа, прошло: " + run.spiritTicks());
            helper.assertTrue(
                    spirit.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(40).equals(data.fields().get("ticks")),
                    "успешный wait обязан положить итог Data{ticks:40}, получено "
                            + spirit.taskResult().map(Object::toString).orElse("пусто"));
            TacticsScene.finish(helper, spirit);
        });
    }

    private static SpiritEntity reload(GameTestHelper helper, SpiritEntity original, CompoundTag tag) {
        original.discard();
        SpiritEntity reloaded = ModEntities.SPIRIT.get().create(helper.getLevel());
        reloaded.load(tag);
        helper.getLevel().addFreshEntity(reloaded);
        return reloaded;
    }

    @GameTest(template = "empty", batch = "social_restart_wait", timeoutTicks = 300)
    public static void wait_SavedMidTask_ResumesFromRemainingTicks(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        AtomicReference<SpiritEntity> holder = new AtomicReference<>(spirit);
        AtomicBoolean saved = new AtomicBoolean(false);
        int[] savedElapsed = {-1};
        int[] reloadedAt = {-1};
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("wait", Map.of("ticks", 40)), 10);
        helper.onEachTick(() -> {
            if (saved.get() || holder.get() != spirit) {
                return;
            }
            if (spirit.taskStatus().orElse(null) == RunStatus.RUNNING && run.spiritTicks() >= 20) {
                saved.set(true);
                savedElapsed[0] = run.spiritTicks();
                CompoundTag tag = new CompoundTag();
                spirit.saveWithoutId(tag);
                SpiritEntity reloaded = reload(helper, spirit, tag);
                holder.set(reloaded);
                reloadedAt[0] = reloaded.tickCount;
            }
        });

        helper.succeedWhen(() -> {
            SpiritEntity current = holder.get();
            run.assertStarted();
            helper.assertTrue(saved.get(), "перезапуск на середине ещё не выполнен");
            helper.assertTrue(
                    current.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "wait после перезапуска обязан завершиться успехом, статус "
                            + current.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + current.taskFailureReason().orElse("нет"));
            int elapsedAfterReload = current.tickCount - reloadedAt[0];
            helper.assertTrue(
                    elapsedAfterReload > 0 && elapsedAfterReload < 40,
                    "wait обязан продолжить с остатка, а не с нуля: после перезагрузки прошло " + elapsedAfterReload
                            + " тиков, а сохранились на " + savedElapsed[0] + "-м из 40");
            helper.assertTrue(
                    current.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(40).equals(data.fields().get("ticks")),
                    "итог wait после перезапуска обязан называть ticks=40, получено "
                            + current.taskResult().map(Object::toString).orElse("пусто"));
            TacticsScene.finish(helper, current);
        });
    }

    @GameTest(template = "arena", batch = "social_restart_deliver", timeoutTicks = 400)
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
            if (saved.get() || holder.get() != spirit) {
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

    @GameTest(template = "arena", batch = "social_restart_deliver_remainder", timeoutTicks = 300)
    public static void deliver_RestoredWithPartialDelivery_TakesOnlyRemainder(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "RemainderTarget");
        TaskSnapshot snapshot = new TaskSnapshot(
                TaskSnapshot.VERSION,
                RunStatus.RUNNING,
                null,
                new ScriptCall("deliver", Map.of("item", "cobblestone", "count", 3, "to", "player:RemainderTarget")),
                new TaskProgress.ScriptAt(Map.of("delivered", 2)));
        CompoundTag tag = new CompoundTag();
        spirit.saveWithoutId(tag);
        SpiritTaskStore.write(tag, TaskSnapshotCodec.encode(snapshot));
        SpiritEntity reloaded = reload(helper, spirit, tag);

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    reloaded.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deliver, восстановленный с delivered=2 из 3, обязан довезти остаток и завершиться успехом, "
                            + "статус "
                            + reloaded.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + reloaded.taskFailureReason().orElse("нет"));
            int withPlayer = GameTestFixtures.countInInventory(player.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    withPlayer == 1,
                    "адресат обязан получить только остаток 1 булыжник, а не все 3: у игрока " + withPlayer);
            int left = GameTestFixtures.countInInventory(reloaded.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    left == 2, "у духа обязано остаться 2 булыжника (3 в инвентаре минус остаток 1), осталось " + left);
            helper.assertTrue(
                    reloaded.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(3).equals(data.fields().get("delivered")),
                    "итог deliver обязан назвать delivered=3 (2 ранее + 1 остаток), получено "
                            + reloaded.taskResult().map(Object::toString).orElse("пусто"));
            TacticsScene.finish(helper, reloaded);
        });
    }

    @GameTest(template = "arena", batch = "social_deliver_leftover", timeoutTicks = 300)
    public static void deliver_AddresseeInventoryFull_LeftoverDroppedBySpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "FullTarget");
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.DIRT, 64));
        }

        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("deliver", Map.of("item", "cobblestone", "count", 3, "to", "player:FullTarget")),
                10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deliver при полном инвентаре адресата обязан завершиться успехом, оставив остаток на земле, "
                            + "статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(inventory, Items.COBBLESTONE) == 0,
                    "полный инвентарь адресата не принимает булыжник: у игрока "
                            + GameTestFixtures.countInInventory(inventory, Items.COBBLESTONE));
            List<ItemEntity> drops = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            helper.assertTrue(
                    drops.size() == 1, "весь остаток обязан упасть одной кучкой на землю, кучек: " + drops.size());
            ItemEntity drop = drops.get(0);
            helper.assertTrue(
                    drop.getItem().getCount() == 3,
                    "на землю обязан упасть весь остаток (3), а упало "
                            + drop.getItem().getCount());
            helper.assertTrue(
                    drop.getOwner() != null && drop.getOwner().getUUID().equals(spirit.getUUID()),
                    "упавший остаток обязан быть помечен духом-доставщиком (DropCollector), а владелец="
                            + drop.getOwner());
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_drop_player", timeoutTicks = 300)
    public static void dropToPlayer_SpiritHasCobble_ItemLandsNearPlayer(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "DropTarget");

        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("drop_to_player", Map.of("item", "cobblestone", "count", 3, "to", "player:DropTarget")),
                10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "drop_to_player обязан дойти до игрока и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int nearPlayer = helper
                    .getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            player.getBoundingBox().inflate(1.5),
                            drop -> !drop.isRemoved() && drop.getItem().is(Items.COBBLESTONE))
                    .stream()
                    .mapToInt(drop -> drop.getItem().getCount())
                    .sum();
            helper.assertTrue(
                    nearPlayer == 3, "рядом с игроком обязано лежать ровно 3 булыжника, фактически: " + nearPlayer);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "после броска у духа не должно остаться булыжников, осталось: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_give_spirit", timeoutTicks = 300)
    public static void give_SpiritAddressee_ReceivesItems(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity giver = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        SpiritEntity receiver = TacticsScene.spirit(helper, new BlockPos(9, 1, 5));
        receiver.setNoAi(true);
        giver.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));

        SpiritRun run = SpiritRun.launch(
                helper,
                giver,
                new ScriptCall("give", Map.of("item", "cobblestone", "count", 3, "to", "spirit:" + receiver.getUUID())),
                10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    giver.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "give обязан передать предмет духу и завершиться успехом, статус: "
                            + giver.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + giver.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(receiver.getInventory(), Items.COBBLESTONE) == 3,
                    "дух-адресат обязан получить ровно 3 булыжника, у него: "
                            + GameTestFixtures.countInInventory(receiver.getInventory(), Items.COBBLESTONE));
            TacticsScene.finish(helper, giver);
        });
    }

    @GameTest(template = "arena", batch = "social_report", timeoutTicks = 150)
    public static void report_PlayerAddressee_GetsStateLine(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        List<Component> messages = new ArrayList<>();
        GameTestFixtures.mockPlayer(helper, new BlockPos(4, 1, 5), "ReportTarget", messages);

        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("report", Map.of("to", "player:ReportTarget")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "report обязан отправить доклад и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String line = messages.stream()
                    .map(Component::getString)
                    .filter(text -> text.contains("Доклад"))
                    .findFirst()
                    .orElse("");
            helper.assertTrue(
                    line.contains("здоровье") && line.contains("инвентарь") && line.contains("где"),
                    "доклад обязан называть здоровье, инвентарь и место, фактически: '" + line + "'");
            helper.assertTrue(
                    line.contains("cobblestone"),
                    "доклад обязан кратко перечислять инвентарь, фактически: '" + line + "'");
            TacticsScene.finish(helper, spirit);
        });
    }
}

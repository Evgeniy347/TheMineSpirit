package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.social.SocialTuning;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SocialGameTests {
    private SocialGameTests() {
    }

    @GameTest(template = "arena", batch = "social_deliver", timeoutTicks = 300)
    public static void deliver_ThreeCobbleToPlayer_PlayerGetsAll(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 3));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "DeliverTarget");

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("deliver", Map.of(
                "item", "cobblestone", "count", 3, "to", "player:DeliverTarget")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deliver обязан довести предмет и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int withPlayer = GameTestFixtures.countInInventory(player.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(withPlayer == 3,
                    "адресат обязан получить ровно 3 булыжника, у игрока: " + withPlayer);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "после доставки у духа не должно остаться булыжников, осталось: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_deliver_missing", timeoutTicks = 150)
    public static void deliver_NoItem_FailsWithNumbers(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(9, 1, 5), "DeliverEmpty");

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("deliver", Map.of(
                "item", "cobblestone", "count", 3, "to", "player:DeliverEmpty")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "deliver без предмета обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не хватает"),
                    "отказ обязан называть нехватку предмета, причина: " + reason);
            helper.assertTrue(reason.contains("3") && reason.contains("0"),
                    "отказ обязан содержать числа (сколько нужно и сколько есть), причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_meet", timeoutTicks = 300)
    public static void meet_PlayerNearby_SpiritArrives(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(9, 1, 5));
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(1, 1, 5), "MeetTarget");

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("meet", Map.of(
                "target", "player:MeetTarget")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "meet обязан подвести духа к игроку и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            double distance = spirit.position().distanceTo(player.position());
            helper.assertTrue(distance <= SocialTuning.MEET_ARRIVAL + 0.5,
                    "дух обязан остановиться на MEET_ARRIVAL (" + SocialTuning.MEET_ARRIVAL
                            + ") от игрока, расстояние: " + distance);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "arena", batch = "social_meet_missing", timeoutTicks = 100)
    public static void meet_TargetMissing_FailsWithText(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(5, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("meet", Map.of(
                "target", "player:NoSuchPlayer")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "meet без цели обязан отказаться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не найдена"),
                    "отказ обязан называть ненайденную цель, причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_say_wait", timeoutTicks = 200)
    public static void say_ShortText_PlayerGetsMessage(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(3, 1, 1), "SayTarget", messages);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("say", Map.of(
                "text", "привет")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "say обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(messages.stream().anyMatch(m -> m.getString().contains("привет")),
                    "игрок обязан получить сообщение с текстом, сообщения: " + messages);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_say_spam", timeoutTicks = 100)
    public static void say_SecondSayImmediately_FailsWithAntiSpam(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));
        List<Component> messages = new ArrayList<>();
        ServerPlayer player = GameTestFixtures.mockPlayer(helper, new BlockPos(3, 1, 1), "SpamTarget", messages);

        spirit.setLastSayTick(spirit.tickCount);

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("say", Map.of(
                "text", "спам")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "второй say сразу обязан отказаться по анти-спаму, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("спам") || reason.contains("интервал"),
                    "отказ обязан называть анти-спам, причина: " + reason);
            helper.assertTrue(reason.contains("20"),
                    "отказ обязан содержать число интервала (20), причина: " + reason);
            TacticsScene.finish(helper, spirit);
        });
    }

    @GameTest(template = "empty", batch = "social_wait", timeoutTicks = 200)
    public static void wait_FortyTicks_SucceedsAfterSpiritTicks(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(1, 1, 1));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("wait", Map.of(
                "ticks", 40)), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "wait 40 тиков обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(run.spiritTicks() >= 40,
                    "wait обязан ждать ровно 40 тиков духа, прошло: " + run.spiritTicks());
            TacticsScene.finish(helper, spirit);
        });
    }
}

package com.theminespirit.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritCommandGameTests {
    private static final String SPIRIT_SELECTOR = spiritSelector(1);

    private SpiritCommandGameTests() {
    }

    private static String spiritSelector(int limit) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,distance=..8,limit=" + limit + "]";
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void commandFollow_MockPlayerSource_SpiritApproachesPlayer(GameTestHelper helper)
            throws CommandSyntaxException {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(4, 1, 1), "gt_follow_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                .getCommands().getDispatcher();

        helper.assertTrue(dispatcher.getRoot().getChild("spirit") != null,
                "Команда /spirit обязана быть зарегистрирована на сервере: без неё оператор не получит канал управления духом");

        double before = spirit.position().distanceTo(player.position());
        int accepted = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " следуй за мной", source);
        helper.assertTrue(accepted == 1,
                "Выполненная игроком цель «следуй за мной» обязана ставить задачу: подстановка заглушки 'player' "
                        + "или потеря имени игрока-источника приводит к отказу, результат=" + accepted);
        helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                "Сразу после команды дух обязан быть в состоянии «выполняется»: команда не запустила задачу. "
                        + "Состояние=" + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());

        helper.runAfterDelay(40, () -> {
            helper.assertTrue(spirit.taskStatus().filter(status -> status != RunStatus.FAILED).isPresent(),
                    "Через 40 тиков сопровождение не должно быть провалено: цель '" + player.getName().getString()
                            + "' не найдена, потому что команда не передала имя игрока. Состояние="
                            + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());
            double after = spirit.position().distanceTo(player.position());
            helper.assertTrue(after < before,
                    "Дух обязан приближаться к игроку-источнику: было " + before + ", стало " + after
                            + ". Иначе follow не выполняет сопровождение");
            removePlayer(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandGoalThenCancel_OnSelectedSpirit_StartsAndCancelsTask(GameTestHelper helper)
            throws CommandSyntaxException {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(1, 1, 1), "gt_cancel_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                .getCommands().getDispatcher();

        int accepted = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " следуй за мной", source);
        helper.assertTrue(accepted == 1,
                "Команда goal обязана вернуть число принявших цель духов: результат=" + accepted);
        helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                "После /spirit goal дух обязан быть в состоянии «выполняется»: команда не запустила задачу. "
                        + "Состояние=" + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());

        int rejected = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " сруби дерево", source);
        helper.assertTrue(rejected == 0,
                "Цель, чья схема не реализована в каталоге духа (scan/chop_tree отсутствуют), обязана вернуть 0 и не "
                        + "заменить выполняемую задачу: результат=" + rejected);
        helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                "Отклонённая команда goal не должна трогать уже выполняемую задачу: статус духа изменился");

        int cancelled = dispatcher.execute("spirit cancel " + SPIRIT_SELECTOR, source);
        helper.assertTrue(cancelled == 1,
                "Команда cancel обязана вернуть число духов с отменённой задачей: результат=" + cancelled);
        helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.CANCELLED).isPresent(),
                "После /spirit cancel задача духа обязана считаться отменённой: команда не остановила задачу");

        removePlayer(helper, player);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandGoal_OnTwoSpirits_AppliesToAllOrNone(GameTestHelper helper)
            throws CommandSyntaxException {
        buildFloor(helper);
        SpiritEntity first = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        SpiritEntity second = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 2));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(4, 1, 1), "gt_pair_player");
        CommandSourceStack source = playerSource(player);
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                .getCommands().getDispatcher();
        String both = spiritSelector(2);

        int rejected = dispatcher.execute("spirit goal " + both + " сруби дерево", source);
        helper.assertTrue(rejected == 0,
                "Цель, не реализуемая в каталоге духов, обязана вернуть 0: результат=" + rejected);
        helper.assertTrue(first.taskStatus().isEmpty() && second.taskStatus().isEmpty(),
                "При отказе планирования ни один дух не должен получить задачу: первый=" + first.taskStatus()
                        + ", второй=" + second.taskStatus());

        int accepted = dispatcher.execute("spirit goal " + both + " следуй за мной", source);
        helper.assertTrue(accepted == 2,
                "Выполнимая цель обязана стать задачей обоим духам: результат=" + accepted);
        helper.assertTrue(first.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent()
                        && second.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                "После /spirit goal оба духа обязаны быть в состоянии «выполняется»: первый=" + first.taskStatus()
                        + ", второй=" + second.taskStatus());

        removePlayer(helper, player);
        helper.succeed();
    }

    private static CommandSourceStack playerSource(ServerPlayer player) {
        return player.server.createCommandSourceStack()
                .withEntity(player)
                .withPosition(player.position())
                .withPermission(4);
    }

    private static ServerPlayer spawnMockPlayer(GameTestHelper helper, BlockPos relative, String name) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void buildFloor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
    }
}

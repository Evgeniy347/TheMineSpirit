package com.theminespirit.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiritCommandGameTests {
    private static final String SPIRIT_SELECTOR = spiritSelector(1);

    // Цель, под которую нет правила в FallbackPlanner и нет скрипта в каталоге: тест отказа не должен
    // ломаться, когда очередной скрипт начнёт реализовывать «сруби дерево» или «добудь руду».
    private static final String UNPLANNABLE_GOAL = "построй дом";

    private SpiritCommandGameTests() {
    }

    private static String spiritSelector(int limit) {
        return "@e[type=" + ModIdentity.MOD_ID + ":spirit,distance=..8,sort=nearest,limit=" + limit + "]";
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void commandFollow_MockPlayerSource_SpiritApproachesPlayer(GameTestHelper helper)
            throws CommandSyntaxException {
        buildFloor(helper);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(4, 1, 1), "gt_follow_player");
        try {
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
                try {
                    helper.assertTrue(spirit.taskStatus().filter(status -> status != RunStatus.FAILED).isPresent(),
                            "Через 40 тиков сопровождение не должно быть провалено: цель '"
                                    + player.getName().getString()
                                    + "' не найдена, потому что команда не передала имя игрока. Состояние="
                                    + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());
                    double after = spirit.position().distanceTo(player.position());
                    helper.assertTrue(after < before,
                            "Дух обязан приближаться к игроку-источнику: было " + before + ", стало " + after
                                    + ". Иначе follow не выполняет сопровождение");
                    helper.succeed();
                } finally {
                    removePlayer(helper, player);
                }
            });
        } catch (RuntimeException | CommandSyntaxException failedBeforeSchedule) {
            removePlayer(helper, player);
            throw failedBeforeSchedule;
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandGoalThenCancel_OnSelectedSpirit_StartsAndCancelsTask(GameTestHelper helper)
            throws CommandSyntaxException {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(1, 1, 1), "gt_cancel_player");
        try {
            CommandSourceStack source = playerSource(player);
            CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                    .getCommands().getDispatcher();

            int accepted = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " следуй за мной", source);
            helper.assertTrue(accepted == 1,
                    "Команда goal обязана вернуть число принявших цель духов: результат=" + accepted);
            helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                    "После /spirit goal дух обязан быть в состоянии «выполняется»: команда не запустила задачу. "
                            + "Состояние=" + spirit.taskStatus() + ", причина=" + spirit.taskFailureReason());

            int rejected = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " " + UNPLANNABLE_GOAL, source);
            helper.assertTrue(rejected == 0,
                    "Цель '" + UNPLANNABLE_GOAL + "', которую не умеет планировщик и не реализует ни один скрипт, "
                            + "обязана вернуть 0 и не заменить выполняемую задачу: результат=" + rejected);
            helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                    "Отклонённая команда goal не должна трогать уже выполняемую задачу: статус духа изменился");

            int cancelled = dispatcher.execute("spirit cancel " + SPIRIT_SELECTOR, source);
            helper.assertTrue(cancelled == 1,
                    "Команда cancel обязана вернуть число духов с отменённой задачей: результат=" + cancelled);
            helper.assertTrue(spirit.taskStatus().filter(status -> status == RunStatus.CANCELLED).isPresent(),
                    "После /spirit cancel задача духа обязана считаться отменённой: команда не остановила задачу");

            helper.succeed();
        } finally {
            removePlayer(helper, player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandGoal_OnTwoSpirits_AppliesToAllOrNone(GameTestHelper helper)
            throws CommandSyntaxException {
        buildFloor(helper);
        SpiritEntity first = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 1));
        SpiritEntity second = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 2));
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(4, 1, 1), "gt_pair_player");
        try {
            CommandSourceStack source = playerSource(player);
            CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                    .getCommands().getDispatcher();
            String both = spiritSelector(2);

            int rejected = dispatcher.execute("spirit goal " + both + " " + UNPLANNABLE_GOAL, source);
            helper.assertTrue(rejected == 0,
                    "Цель '" + UNPLANNABLE_GOAL + "', которую не умеет планировщик и не реализует ни один скрипт, "
                            + "обязана вернуть 0: результат=" + rejected);
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

            helper.succeed();
        } finally {
            removePlayer(helper, player);
        }
    }

    @GameTest(template = "arena", batch = "spirit_goal", timeoutTicks = 200)
    public static void commandGoal_TwoSpiritsOnArena_PlansFromEachOwnObservation(GameTestHelper helper)
            throws CommandSyntaxException {
        GameTestFixtures.reset(helper);
        helper.setBlock(0, 0, 0, Blocks.STONE);
        helper.setBlock(10, 0, 10, Blocks.STONE);
        // Тег уникален на запуск: мир переживает прогоны, и духи с прошлым тегом попали бы в селектор.
        String tag = "observe_each_" + UUID.randomUUID();
        SpiritEntity first = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        first.addTag(tag);
        SpiritEntity second = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(10, 1, 10));
        second.addTag(tag);
        second.getInventory().setItem(0, new ItemStack(Items.IRON_AXE, 1));
        helper.assertTrue(GameTestFixtures.countInInventory(second.getInventory(), Items.IRON_AXE) == 1,
                "Подготовка обязана положить топор в инвентарь второго духа: без разного инвентаря наблюдения "
                        + "духов неразличимы и тест не доказывает по-духовые наблюдения");
        ServerPlayer player = spawnMockPlayer(helper, new BlockPos(5, 1, 5), "gt_observe_each");
        try {
            CommandSourceStack source = playerSource(player);
            CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                    .getCommands().getDispatcher();
            String both = "@e[type=" + ModIdentity.MOD_ID + ":spirit,tag=" + tag + "]";

            int accepted = dispatcher.execute("spirit goal " + both + " следуй за мной", source);
            helper.assertTrue(accepted == 2,
                    "Одна команда на двух духов обязана ставить задачу обоим: общее наблюдение первого духа "
                            + "сторож BatchPlanner отклоняет, и задача не ставится никому. Результат=" + accepted);
            helper.assertTrue(first.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent()
                            && second.taskStatus().filter(status -> status == RunStatus.RUNNING).isPresent(),
                    "После /spirit goal оба духа обязаны выполняться: первый=" + first.taskStatus()
                            + ", второй=" + second.taskStatus());

            helper.succeed();
        } finally {
            first.discard();
            second.discard();
            removePlayer(helper, player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void commandFollow_ServerSource_RefusesWithoutTask(GameTestHelper helper)
            throws CommandSyntaxException {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        CommandSourceStack base = helper.getLevel().getServer().createCommandSourceStack()
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))))
                .withPermission(4);
        List<String> failures = new ArrayList<>();
        CommandSourceStack source = base.withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                failures.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return base.source.acceptsSuccess();
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        });
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer()
                .getCommands().getDispatcher();

        int accepted = dispatcher.execute("spirit goal " + SPIRIT_SELECTOR + " следуй за мной", source);

        helper.assertTrue(accepted == 0,
                "Цель 'за мной' без игрока-источника обязана возвращать 0, а не ставиться с пустым target: "
                        + "результат=" + accepted);
        helper.assertTrue(spirit.taskStatus().isEmpty(),
                "Отказ без игрока-источника не должен запускать задачу: у духа появился статус "
                        + spirit.taskStatus());
        helper.assertTrue(failures.size() == 1
                        && failures.get(0).contains("требует игрока-источника")
                        && failures.get(0).contains("следуй за мной"),
                "Отказ без игрока-источника обязан называть причину текстом с целью и подсказкой: молчаливый отказ "
                        + "или чужой текст не объяснит оператору, что команду надо выполнить от имени игрока. "
                        + "Получено: " + failures);
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
